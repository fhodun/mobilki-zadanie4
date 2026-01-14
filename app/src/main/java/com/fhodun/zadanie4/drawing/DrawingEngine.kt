package com.fhodun.zadanie4.drawing

import android.graphics.Color
import android.graphics.Path

class DrawingEngine(
    initialColor: Int = Color.RED,
) {
    var currentColor: Int = initialColor
        private set

    val currentPath: Path = Path()

    private var startX = 0f
    private var startY = 0f

    private var hasActiveStroke = false

    fun setColor(color: Int) {
        currentColor = color
    }

    fun clear() {
        currentPath.reset()
        hasActiveStroke = false
    }

    fun onDown(x: Float, y: Float) {
        currentPath.reset()
        startX = x
        startY = y
        currentPath.moveTo(x, y)
        hasActiveStroke = true
    }

    fun onMove(x: Float, y: Float) {
        if (!hasActiveStroke) return
        currentPath.lineTo(x, y)
    }

    fun onUpOrCancel(x: Float, y: Float): StrokeCommit? {
        if (!hasActiveStroke) return null

        currentPath.lineTo(x, y)

        val commit = StrokeCommit(
            path = Path(currentPath),
            color = currentColor,
            startX = startX,
            startY = startY,
            endX = x,
            endY = y,
        )

        currentPath.reset()
        hasActiveStroke = false
        return commit
    }
}

data class StrokeCommit(
    val path: Path,
    val color: Int,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
)

