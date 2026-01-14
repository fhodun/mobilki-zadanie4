package com.fhodun.zadanie4

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.TextureView
import com.fhodun.zadanie4.drawing.DrawingEngine
import kotlin.math.max

class DrawingTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {

    private val lock = Any()

    private var bufferBitmap: Bitmap? = null
    private var bufferCanvas: Canvas? = null

    private val engine = DrawingEngine(initialColor = Color.RED)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = dpToPx(8f)
        color = engine.currentColor
    }

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = engine.currentColor
    }

    private val markerRadiusPx: Float = dpToPx(6f)

    private val previewPath = Path()

    init {
        surfaceTextureListener = this
        isOpaque = true
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun setColor(color: Int) {
        synchronized(lock) {
            engine.setColor(color)
            linePaint.color = color
            markerPaint.color = color
        }
        redraw()
    }

    fun clear() {
        synchronized(lock) {
            engine.clear()
            bufferCanvas?.drawColor(Color.WHITE)
        }
        redraw()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        synchronized(lock) {
            bufferBitmap?.recycle()
            bufferBitmap = null
            bufferCanvas = null
            engine.clear()
        }
    }

    override fun onSurfaceTextureAvailable(
        surface: android.graphics.SurfaceTexture,
        width: Int,
        height: Int
    ) {
        ensureBuffer(width, height)
        redraw()
    }

    override fun onSurfaceTextureSizeChanged(
        surface: android.graphics.SurfaceTexture,
        width: Int,
        height: Int
    ) {
        ensureBuffer(width, height)
        redraw()
    }

    override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
        return true
    }

    override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                if (!hasFocus()) requestFocus()

                synchronized(lock) {
                    if (bufferBitmap == null && width > 0 && height > 0) {
                        ensureBuffer(width, height)
                    }
                    engine.onDown(event.x, event.y)
                }
                redraw()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                synchronized(lock) {
                    engine.onMove(event.x, event.y)
                }
                redraw()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val commit = synchronized(lock) { engine.onUpOrCancel(event.x, event.y) }

                if (commit != null) {
                    synchronized(lock) {
                        bufferCanvas?.let { bc ->
                            bc.drawPath(commit.path, linePaint)
                            bc.drawCircle(commit.startX, commit.startY, markerRadiusPx, markerPaint)
                            bc.drawCircle(commit.endX, commit.endY, markerRadiusPx, markerPaint)
                        }
                    }
                }

                performClick()
                redraw()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun redraw() {
        if (!isAvailable) return

        val canvas = try {
            lockCanvas()
        } catch (_: Exception) {
            null
        } ?: return

        try {
            drawFrame(canvas)
        } finally {
            try {
                unlockCanvasAndPost(canvas)
            } catch (_: Exception) {
            }
        }
    }

    private fun drawFrame(canvas: Canvas) {
        val bmp: Bitmap?

        synchronized(lock) {
            if (bufferBitmap == null && width > 0 && height > 0) {
                ensureBuffer(width, height)
            }

            bmp = bufferBitmap
            previewPath.set(engine.currentPath)
        }

        canvas.drawColor(Color.WHITE)
        if (bmp != null) canvas.drawBitmap(bmp, 0f, 0f, null)
        if (!previewPath.isEmpty) canvas.drawPath(previewPath, linePaint)
    }

    private fun ensureBuffer(w: Int, h: Int) {
        val safeW = max(1, w)
        val safeH = max(1, h)

        synchronized(lock) {
            val existing = bufferBitmap
            if (existing != null && existing.width == safeW && existing.height == safeH) return

            bufferBitmap?.recycle()
            bufferBitmap = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
            bufferCanvas = Canvas(bufferBitmap!!).apply { drawColor(Color.WHITE) }
        }
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}
