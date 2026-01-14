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
import kotlin.math.max

/**
 * Wariant oparty o TextureView.
 * Na emulatorach bywa pewniejszy niż SurfaceView (zwłaszcza w kwestii inputu i warstw).
 * Architektura taka sama: osobny wątek renderujący + bitmapa jako trwały bufor.
 */
class DrawingTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextureView(context, attrs), TextureView.SurfaceTextureListener, Runnable {

    private val lock = Any()

    @Volatile
    private var running = false

    @Volatile
    private var textureAvailable = false

    private var renderThread: Thread? = null

    private var bufferBitmap: Bitmap? = null
    private var bufferCanvas: Canvas? = null

    private val currentPath = Path()

    private var currentColorInt: Int = Color.RED

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = currentColorInt
        strokeWidth = dpToPx(8f)
    }

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = currentColorInt
    }

    private val markerRadiusPx: Float = dpToPx(6f)

    private var startX = 0f
    private var startY = 0f

    init {
        surfaceTextureListener = this
        isOpaque = true
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun setColor(color: Int) {
        synchronized(lock) {
            currentColorInt = color
            linePaint.color = color
            markerPaint.color = color
        }
    }

    fun clear() {
        synchronized(lock) {
            currentPath.reset()
            bufferCanvas?.drawColor(Color.WHITE)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopThread()
        synchronized(lock) {
            bufferBitmap?.recycle()
            bufferBitmap = null
            bufferCanvas = null
            currentPath.reset()
        }
    }

    override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
        textureAvailable = true
        ensureBuffer(width, height)
        startThreadIfNeeded()
    }

    override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
        ensureBuffer(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
        textureAvailable = false
        stopThread()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {
        // no-op
    }

    private fun startThreadIfNeeded() {
        if (running) return
        if (!textureAvailable) return

        running = true
        renderThread = Thread(this, "DrawingTextureView-Render").also { it.start() }
    }

    private fun stopThread() {
        running = false
        renderThread?.interrupt()
        try {
            renderThread?.join(1000)
        } catch (_: InterruptedException) {
        }
        renderThread = null
    }

    override fun run() {
        while (running) {
            if (!textureAvailable || !isAvailable) {
                sleepQuietly(16)
                continue
            }

            val c: Canvas? = try {
                lockCanvas()
            } catch (_: Exception) {
                null
            }

            if (c == null) {
                sleepQuietly(16)
                continue
            }

            try {
                drawFrame(c)
            } catch (_: Exception) {
                // Jeśli texture w trakcie renderu zniknie, nie wywracamy aplikacji.
            } finally {
                try {
                    unlockCanvasAndPost(c)
                } catch (_: Exception) {
                }
            }

            sleepQuietly(16)
        }
    }

    private fun drawFrame(canvas: Canvas) {
        val bmp: Bitmap?
        val pathCopy = Path()
        val paintCopy: Paint

        synchronized(lock) {
            if (bufferBitmap == null && width > 0 && height > 0) {
                ensureBuffer(width, height)
            }
            bmp = bufferBitmap
            pathCopy.set(currentPath)
            paintCopy = Paint(linePaint)
        }

        canvas.drawColor(Color.WHITE)
        if (bmp != null) canvas.drawBitmap(bmp, 0f, 0f, null)
        if (!pathCopy.isEmpty) canvas.drawPath(pathCopy, paintCopy)
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
                    currentPath.reset()
                    startX = event.x
                    startY = event.y
                    currentPath.moveTo(startX, startY)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                synchronized(lock) {
                    currentPath.lineTo(event.x, event.y)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val endX = event.x
                val endY = event.y

                synchronized(lock) {
                    currentPath.lineTo(endX, endY)
                    bufferCanvas?.let { bc ->
                        bc.drawPath(currentPath, linePaint)
                        bc.drawCircle(startX, startY, markerRadiusPx, markerPaint)
                        bc.drawCircle(endX, endY, markerRadiusPx, markerPaint)
                    }
                    currentPath.reset()
                }

                performClick()
                return true
            }
        }

        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
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

    private fun sleepQuietly(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
        }
    }
}
