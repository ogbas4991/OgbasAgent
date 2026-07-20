package com.mobileagent.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.mobileagent.AgentAccessibilityService
import com.mobileagent.AgentForegroundService
import com.mobileagent.R
import com.mobileagent.data.AgentLog
import com.mobileagent.data.LogType
import com.mobileagent.data.Prefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AgentControlActivity : AppCompatActivity() {

    private lateinit var tvCurrentTask: TextView
    private lateinit var tvStepCounter: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerAgentLog: androidx.recyclerview.widget.RecyclerView
    private lateinit var btnStop: Button
    private lateinit var btnNewTask: Button

    private val logAdapter = LogAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agent_control)
        supportActionBar?.title = "Agent Control"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        tvCurrentTask = findViewById(R.id.tvCurrentTask)
        tvStepCounter = findViewById(R.id.tvStepCounter)
        progressBar = findViewById(R.id.progressBar)
        recyclerAgentLog = findViewById(R.id.recyclerAgentLog)
        btnStop = findViewById(R.id.btnStop)
        btnNewTask = findViewById(R.id.btnNewTask)

        recyclerAgentLog.layoutManager = LinearLayoutManager(this)
        recyclerAgentLog.adapter = logAdapter

        val task = intent.getStringExtra("task") ?: AgentForegroundService.currentTask
        tvCurrentTask.text = task
        tvStepCounter.text = ""

        btnStop.setOnClickListener {
            AgentForegroundService().stopAgent()
            Toast.makeText(this, "Agent stopped", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnNewTask.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }

        // Live-update logs from the accessibility service
        lifecycleScope.launch {
            while (true) {
                AgentAccessibilityService.instance?.let { svc ->
                    val logs = svc.logs.value ?: emptyList()
                    logAdapter.submitList(logs)
                    if (logs.isNotEmpty()) {
                        recyclerAgentLog.scrollToPosition(logs.size - 1)
                    }

                    val stepCount = logs.count { it.type == LogType.ACTION }
                    val maxSteps = Prefs.maxSteps
                    if (stepCount > 0) {
                        tvStepCounter.text = "Step $stepCount / $maxSteps"
                        progressBar.visibility = View.VISIBLE
                        progressBar.max = maxSteps
                        progressBar.progress = stepCount
                    }

                    val lastLog = logs.lastOrNull()
                    if (lastLog?.type == LogType.RESULT && lastLog.message.contains("completed")) {
                        tvStepCounter.text = "Completed"
                        progressBar.progress = progressBar.max
                    }
                    if (lastLog?.type == LogType.ERROR && lastLog.message.contains("Max steps")) {
                        tvStepCounter.text = "Max steps reached"
                    }
                }
                delay(500)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}