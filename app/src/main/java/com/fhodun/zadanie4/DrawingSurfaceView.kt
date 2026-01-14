package com.fhodun.zadanie4

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.max

class DrawingSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    private val holderLock = Any()

    @Volatile
    private var running = false
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
        holder.addCallback(this)

        // W praktyce pomaga na emulatorach/niektórych urządzeniach, gdy SurfaceView "nie łapie" dotyku.
        isClickable = true
        isLongClickable = true
        isFocusable = true
        isFocusableInTouchMode = true

        // Upewnij się, że nie robimy żadnych dziwnych overlayów.
        setZOrderOnTop(false)
        setZOrderMediaOverlay(false)

        // Ustaw format, by uniknąć przezroczystości/warstwowania.
        holder.setFormat(android.graphics.PixelFormat.OPAQUE)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            ensureBuffer(w, h)
        }
    }

    fun setColor(color: Int) {
        synchronized(holderLock) {
            currentColorInt = color
            linePaint.color = color
            markerPaint.color = color
        }
    }

    fun clear() {
        synchronized(holderLock) {
            currentPath.reset()
            bufferCanvas?.drawColor(Color.WHITE)
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        renderThread = Thread(this, "DrawingSurfaceView-Render").also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        ensureBuffer(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        renderThread?.joinSafely()
        renderThread = null
    }

    override fun run() {
        while (running) {
            if (!holder.surface.isValid) {
                sleepQuietly(16)
                continue
            }

            val surfaceCanvas = try {
                holder.lockCanvas()
            } catch (_: Exception) {
                null
            }

            if (surfaceCanvas != null) {
                try {
                    drawFrame(surfaceCanvas)
                } finally {
                    try {
                        holder.unlockCanvasAndPost(surfaceCanvas)
                    } catch (_: Exception) {
                    }
                }
            }

            sleepQuietly(16)
        }
    }

    private fun drawFrame(surfaceCanvas: Canvas) {
        val bmp: Bitmap?
        val pathCopy = Path()
        val paintCopy: Paint

        synchronized(holderLock) {
            if (bufferBitmap == null && width > 0 && height > 0) {
                ensureBuffer(width, height)
            }

            bmp = bufferBitmap
            pathCopy.set(currentPath)
            paintCopy = Paint(linePaint)
        }

        surfaceCanvas.drawColor(Color.WHITE)

        if (bmp != null) {
            surfaceCanvas.drawBitmap(bmp, 0f, 0f, null)
        }

        if (!pathCopy.isEmpty) {
            surfaceCanvas.drawPath(pathCopy, paintCopy)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                if (!hasFocus()) requestFocus()

                synchronized(holderLock) {
                    if (bufferBitmap == null && width > 0 && height > 0) {
                        ensureBuffer(width, height)
                    }

                    currentPath.reset()
                    startX = event.x
                    startY = event.y
                    currentPath.moveTo(startX, startY)
                }

                // Natychmiastowy feedback: kropka na powierzchni (pomaga też zdiagnozować input).
                drawTouchDotOnSurface(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                synchronized(holderLock) {
                    currentPath.lineTo(event.x, event.y)
                }

                // Natychmiastowy feedback.
                drawTouchDotOnSurface(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val endX = event.x
                val endY = event.y

                synchronized(holderLock) {
                    currentPath.lineTo(endX, endY)

                    bufferCanvas?.let { c ->
                        c.drawPath(currentPath, linePaint)
                        c.drawCircle(startX, startY, markerRadiusPx, markerPaint)
                        c.drawCircle(endX, endY, markerRadiusPx, markerPaint)
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

    private fun drawTouchDotOnSurface(x: Float, y: Float) {
        if (!holder.surface.isValid) return
        val c = try {
            holder.lockCanvas()
        } catch (_: Exception) {
            null
        }
        if (c != null) {
            try {
                // dorysuj aktualną bitmapę + kropkę (bez czekania na loop)
                val bmp = synchronized(holderLock) { bufferBitmap }
                c.drawColor(Color.WHITE)
                if (bmp != null) c.drawBitmap(bmp, 0f, 0f, null)
                c.drawCircle(x, y, markerRadiusPx, markerPaint)
            } finally {
                try {
                    holder.unlockCanvasAndPost(c)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun ensureBuffer(w: Int, h: Int) {
        synchronized(holderLock) {
            val safeW = max(1, w)
            val safeH = max(1, h)

            val existing = bufferBitmap
            if (existing != null && existing.width == safeW && existing.height == safeH) {
                return
            }

            bufferBitmap?.recycle()
            bufferBitmap = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
            bufferCanvas = Canvas(bufferBitmap!!).apply {
                drawColor(Color.WHITE)
            }
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    private fun sleepQuietly(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
        }
    }

    private fun Thread.joinSafely() {
        try {
            join(1000)
        } catch (_: InterruptedException) {
        }
    }
}
