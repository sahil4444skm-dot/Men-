package com.example.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val status = findViewById<TextView>(R.id.status)
        val keyBox = findViewById<EditText>(R.id.apiKey)
        val prefs = getSharedPreferences("jarvis_settings", MODE_PRIVATE)
        keyBox.setText(prefs.getString("api_key", ""))

        findViewById<Button>(R.id.btnSaveKey).setOnClickListener {
            prefs.edit().putString("api_key", keyBox.text.toString().trim()).apply()
            Toast.makeText(this, "API key save ho gayi", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (!hasMicPermission()) {
                askPermissions()
                return@setOnClickListener
            }
            ContextCompat.startForegroundService(this, Intent(this, JarvisService::class.java))
            status.text = "Jarvis chal raha hai (5 minute)"
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, JarvisService::class.java))
            status.text = "Jarvis band hai"
        }

        findViewById<Button>(R.id.btnAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun askPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 1)
    }
}
