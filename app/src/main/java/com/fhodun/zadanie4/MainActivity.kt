package com.fhodun.zadanie4

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val drawingSurface = findViewById<DrawingTextureView>(R.id.drawingSurface)

        findViewById<View>(R.id.btnRed).setOnClickListener {
            drawingSurface.setColor(Color.RED)
        }
        findViewById<View>(R.id.btnYellow).setOnClickListener {
            drawingSurface.setColor(Color.YELLOW)
        }
        findViewById<View>(R.id.btnBlue).setOnClickListener {
            drawingSurface.setColor(Color.BLUE)
        }
        findViewById<View>(R.id.btnGreen).setOnClickListener {
            drawingSurface.setColor(Color.GREEN)
        }

        findViewById<View>(R.id.btnClear).setOnClickListener {
            drawingSurface.clear()
        }
    }
}