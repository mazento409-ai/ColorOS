package com.glass.dialer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment

class MainActivity : AppCompatActivity() {

    private class Tab(val box: View, val icon: ImageView, val label: TextView)

    private lateinit var tabs: List<Tab>
    private var current = 0

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            select(current)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        tabs = listOf(
            Tab(findViewById(R.id.tab_calls), findViewById(R.id.icon_calls), findViewById(R.id.label_calls)),
            Tab(findViewById(R.id.tab_contacts), findViewById(R.id.icon_contacts), findViewById(R.id.label_contacts)),
            Tab(findViewById(R.id.tab_settings), findViewById(R.id.icon_settings), findViewById(R.id.label_settings))
        )
        tabs.forEachIndexed { i, t -> t.box.setOnClickListener { select(i) } }
        select(0)

        val missing = listOf(
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
    }

    private fun select(index: Int) {
        current = index
        tabs.forEachIndexed { i, t ->
            val active = i == index
            val color = ContextCompat.getColor(
                this, if (active) R.color.accent_blue else R.color.tab_inactive
            )
            t.icon.setColorFilter(color)
            t.label.setTextColor(color)
            t.label.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
        }
        val fragment: Fragment = when (index) {
            0 -> CallsFragment()
            1 -> ContactsFragment()
            else -> SettingsFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
