package com.fhodun.zadanie4

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val drawingView = findViewById<DrawingTextureView>(R.id.drawingView)

        findViewById<View>(R.id.btnRed).setOnClickListener {
            drawingView.setColor(Color.RED)
        }
        findViewById<View>(R.id.btnYellow).setOnClickListener {
            drawingView.setColor(Color.YELLOW)
        }
        findViewById<View>(R.id.btnBlue).setOnClickListener {
            drawingView.setColor(Color.BLUE)
        }
        findViewById<View>(R.id.btnGreen).setOnClickListener {
            drawingView.setColor(Color.GREEN)
        }

        findViewById<View>(R.id.btnClear).setOnClickListener {
            drawingView.clear()
        }
    }
}