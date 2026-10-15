package com.glass.dialer

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class DialpadActivity : AppCompatActivity() {

    private lateinit var display: TextView
    private val number = StringBuilder()

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) dial()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_dialpad)

        val root = findViewById<View>(R.id.dp_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        display = findViewById(R.id.display)
        intent?.data?.let {
            if (it.scheme == "tel") number.append(Uri.decode(it.schemeSpecificPart))
        }

        buildKeys(findViewById(R.id.keys))

        findViewById<View>(R.id.btn_call).setOnClickListener { dial() }
        findViewById<View>(R.id.btn_back).apply {
            setOnClickListener {
                if (number.isNotEmpty()) number.deleteCharAt(number.length - 1)
                update()
            }
            setOnLongClickListener {
                number.setLength(0)
                update()
                true
            }
        }
        update()
    }

    private fun buildKeys(container: LinearLayout) {
        val rows = listOf(
            listOf("1" to "", "2" to "ABC", "3" to "DEF"),
            listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
            listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
            listOf("*" to "", "0" to "+", "#" to "")
        )
        val inflater = LayoutInflater.from(this)
        for (row in rows) {
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (12 * resources.displayMetrics.density).toInt() }
            }
            for ((digit, letters) in row) {
                val cell = FrameLayout(this)
                cell.layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
                val key = inflater.inflate(R.layout.dialpad_key, cell, false)
                key.findViewById<TextView>(R.id.key_digit).text = digit
                key.findViewById<TextView>(R.id.key_letters).text = letters
                key.setOnClickListener { append(digit) }
                if (digit == "0") {
                    key.setOnLongClickListener {
                        append("+")
                        true
                    }
                }
                cell.addView(key)
                line.addView(cell)
            }
            container.addView(line)
        }
    }

    private fun append(s: String) {
        number.append(s)
        update()
    }

    private fun update() {
        display.text = number.toString()
        display.textSize = if (number.length <= 12) 34f else 26f
    }

    private fun dial() {
        if (number.isEmpty()) return
        val granted = checkSelfPermission(Manifest.permission.CALL_PHONE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            CallHelper.call(this, number.toString())
        } else {
            permLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }
}
