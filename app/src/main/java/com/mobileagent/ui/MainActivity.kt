package com.mobileagent.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.mobileagent.AgentAccessibilityService
import com.mobileagent.AgentForegroundService
import com.mobileagent.R
import com.mobileagent.data.Prefs
import com.mobileagent.voice.VoiceInputHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var etTask: EditText
    private lateinit var btnVoice: ImageButton
    private lateinit var btnSend: Button
    private lateinit var btnSettings: ImageButton
    private lateinit var chipOpenSettings: com.google.android.material.chip.Chip
    private lateinit var chipOpenAppInfo: com.google.android.material.chip.Chip
    private lateinit var chipViewLogs: com.google.android.material.chip.Chip
    private lateinit var recyclerLogs: androidx.recyclerview.widget.RecyclerView
    private lateinit var tvEmpty: TextView

    private val logAdapter = LogAdapter()
    private var voiceHelper: VoiceInputHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Prefs.init(this)

        statusDot = findViewById(R.id.statusDot)
        statusText = findViewById(R.id.statusText)
        etTask = findViewById(R.id.etTask)
        btnVoice = findViewById(R.id.btnVoice)
        btnSend = findViewById(R.id.btnSend)
        btnSettings = findViewById(R.id.btnSettings)
        chipOpenSettings = findViewById(R.id.chipOpenSettings)
        chipOpenAppInfo = findViewById(R.id.chipOpenAppInfo)
        chipViewLogs = findViewById(R.id.chipViewLogs)
        recyclerLogs = findViewById(R.id.recyclerLogs)
        tvEmpty = findViewById(R.id.tvEmpty)

        recyclerLogs.layoutManager = LinearLayoutManager(this)
        recyclerLogs.adapter = logAdapter

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnSend.setOnClickListener { submitTask() }

        btnVoice.setOnClickListener { startVoiceInput() }

        chipOpenSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        chipOpenAppInfo.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }

        chipViewLogs.setOnClickListener {
            startActivity(Intent(this, AgentControlActivity::class.java))
        }

        if (!Prefs.hasApiKey()) {
            Toast.makeText(this, R.string.open_settings, Toast.LENGTH_LONG).show()
        }

        // Poll accessibility service status
        lifecycleScope.launch {
            while (true) {
                updateServiceStatus()
                delay(2000)
            }
        }

        // Poll logs
        lifecycleScope.launch {
            while (true) {
                AgentAccessibilityService.instance?.let { svc ->
                    val logs = svc.logs.value ?: emptyList()
                    if (logs.isNotEmpty()) {
                        logAdapter.submitList(logs)
                        tvEmpty.visibility = View.GONE
                        recyclerLogs.visibility = View.VISIBLE
                    } else {
                        tvEmpty.visibility = View.VISIBLE
                        recyclerLogs.visibility = View.GONE
                    }
                }
                delay(500)
            }
        }
    }

    private fun submitTask() {
        val task = etTask.text.toString().trim()
        if (task.isBlank()) {
            Toast.makeText(this, "Please enter a task", Toast.LENGTH_SHORT).show()
            return
        }

        if (!Prefs.hasApiKey()) {
            Toast.makeText(this, R.string.open_settings, Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }

        if (AgentAccessibilityService.instance == null) {
            AlertDialog.Builder(this)
                .setTitle("Accessibility Service Required")
                .setMessage(
                    "Please enable the MobileAgent accessibility service first.\n\n" +
                    "1. Tap 'Open Accessibility Settings'\n" +
                    "2. Find 'MobileAgent Screen Control'\n" +
                    "3. Enable it\n\n" +
                    "If blocked, go to App Info > Allow restricted settings."
                )
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        AgentAccessibilityService.instance?.clearLogs()
        logAdapter.clear()

        val intent = Intent(this, AgentForegroundService::class.java).apply {
            putExtra("task", task)
        }
        startForegroundService(intent)

        startActivity(Intent(this, AgentControlActivity::class.java).apply {
            putExtra("task", task)
        })

        etTask.text.clear()
    }

    private fun startVoiceInput() {
        voiceHelper = VoiceInputHelper(this) { text ->
            etTask.setText(text)
            voiceHelper = null
        }
        voiceHelper?.start()
    }

    private fun updateServiceStatus() {
        val running = AgentAccessibilityService.isRunning
        if (running) {
            statusDot.setBackgroundResource(R.color.success)
            statusText.text = getString(R.string.service_running)
        } else {
            statusDot.setBackgroundResource(R.color.error)
            statusText.text = getString(R.string.service_not_running)
        }
    }

    override fun onDestroy() {
        voiceHelper?.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        voiceHelper?.onActivityResult(requestCode, resultCode, data)
    }
}