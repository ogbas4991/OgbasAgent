package com.mobileagent.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mobileagent.R
import com.mobileagent.data.Prefs
import com.mobileagent.telegram.TelegramBotManager

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.title = "Settings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val etBaseUrl = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etBaseUrl)
        val etApiKey = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etApiKey)
        val etModel = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etModel)
        val etTelegramToken = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etTelegramToken)
        val etMaxSteps = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etMaxSteps)
        val etStepDelay = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etStepDelay)
        val switchTelegram = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchTelegram)
        val switchConfirmDestructive = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchConfirmDestructive)
        val tilTelegramToken = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilTelegramToken)
        val btnSave = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave)

        // OpenRouter dedicated fields
        val etOpenRouterKey = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etOpenRouterKey)
        val etOpenRouterModel = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etOpenRouterModel)
        val btnUseOpenRouter = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUseOpenRouter)

        // NVIDIA NIM dedicated fields
        val etNimKey = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNimKey)
        val etNimModel = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNimModel)
        val btnUseNim = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUseNim)

        // Load current values
        etBaseUrl.setText(Prefs.baseUrl)
        etApiKey.setText(Prefs.apiKey)
        etModel.setText(Prefs.model)
        etTelegramToken.setText(Prefs.telegramBotToken)
        etMaxSteps.setText(Prefs.maxSteps.toString())
        etStepDelay.setText(Prefs.stepDelayMs.toString())
        switchTelegram.isChecked = Prefs.telegramEnabled
        switchConfirmDestructive.isChecked = Prefs.confirmDestructive
        tilTelegramToken.isEnabled = Prefs.telegramEnabled

        // Load dedicated provider keys
        etOpenRouterKey.setText(Prefs.openRouterKey)
        etOpenRouterModel.setText(Prefs.openRouterModel)
        etNimKey.setText(Prefs.nimKey)
        etNimModel.setText(Prefs.nimModel)

        // Provider quick-select chips — auto-fills URL, model AND API key
        findViewById<com.google.android.material.chip.Chip>(R.id.chipOpenRouter).setOnClickListener {
            etBaseUrl.setText("https://openrouter.ai/api/v1")
            etModel.setText("openai/gpt-oss-120b:free")
            // Auto-fill key from dedicated field if available
            val dedicatedKey = etOpenRouterKey.text.toString().trim()
            if (dedicatedKey.isNotBlank()) etApiKey.setText(dedicatedKey)
        }
        findViewById<com.google.android.material.chip.Chip>(R.id.chipDeepSeek).setOnClickListener {
            etBaseUrl.setText("https://api.deepseek.com/v1")
            etModel.setText("deepseek-chat")
        }
        findViewById<com.google.android.material.chip.Chip>(R.id.chipOllama).setOnClickListener {
            etBaseUrl.setText("http://10.0.2.2:11434/v1")
            etModel.setText("llama3")
            etApiKey.setText("ollama")
        }
        findViewById<com.google.android.material.chip.Chip>(R.id.chipNvidiaNim).setOnClickListener {
            etBaseUrl.setText("https://integrate.api.nvidia.com/v1")
            etModel.setText("meta/llama-3.1-405b-instruct")
            // Auto-fill key from dedicated field if available
            val dedicatedKey = etNimKey.text.toString().trim()
            if (dedicatedKey.isNotBlank()) etApiKey.setText(dedicatedKey)
        }

        // "Use OpenRouter" button — copies dedicated key/model into main fields
        btnUseOpenRouter.setOnClickListener {
            val key = etOpenRouterKey.text.toString().trim()
            if (key.isBlank()) {
                Toast.makeText(this, "Enter your OpenRouter API key first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            etBaseUrl.setText("https://openrouter.ai/api/v1")
            etApiKey.setText(key)
            val model = etOpenRouterModel.text.toString().trim()
                .ifBlank { "openai/gpt-oss-120b:free" }
            etModel.setText(model)
            Toast.makeText(this, "Switched to OpenRouter", Toast.LENGTH_SHORT).show()
        }

        // "Use NVIDIA NIM" button — copies dedicated key/model into main fields
        btnUseNim.setOnClickListener {
            val key = etNimKey.text.toString().trim()
            if (key.isBlank()) {
                Toast.makeText(this, "Enter your NVIDIA NIM API key first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            etBaseUrl.setText("https://integrate.api.nvidia.com/v1")
            etApiKey.setText(key)
            val model = etNimModel.text.toString().trim()
                .ifBlank { "meta/llama-3.1-405b-instruct" }
            etModel.setText(model)
            Toast.makeText(this, "Switched to NVIDIA NIM", Toast.LENGTH_SHORT).show()
        }

        switchTelegram.setOnCheckedChangeListener { _, checked ->
            tilTelegramToken.isEnabled = checked
        }

        btnSave.setOnClickListener {
            val baseUrl = etBaseUrl.text.toString().trim()
            var apiKey = etApiKey.text.toString().trim()
            val model = etModel.text.toString().trim()

            // Save dedicated provider keys
            val openRouterKey = etOpenRouterKey.text.toString().trim()
            val nimKey = etNimKey.text.toString().trim()
            Prefs.openRouterKey = openRouterKey
            Prefs.openRouterModel = etOpenRouterModel.text.toString().trim()
            Prefs.nimKey = nimKey
            Prefs.nimModel = etNimModel.text.toString().trim()

            // Auto-sync: if main apiKey is empty, pull from dedicated provider field
            if (apiKey.isBlank()) {
                apiKey = when {
                    baseUrl.contains("openrouter.ai") -> openRouterKey
                    baseUrl.contains("nvidia.com") -> nimKey
                    else -> ""
                }
                if (apiKey.isNotBlank()) {
                    etApiKey.setText(apiKey)
                }
            }

            Prefs.baseUrl = baseUrl
            Prefs.apiKey = apiKey
            Prefs.model = model
            Prefs.telegramBotToken = etTelegramToken.text.toString().trim()
            Prefs.maxSteps = etMaxSteps.text.toString().toIntOrNull() ?: 30
            Prefs.stepDelayMs = etStepDelay.text.toString().toLongOrNull() ?: 1500L
            Prefs.telegramEnabled = switchTelegram.isChecked
            Prefs.confirmDestructive = switchConfirmDestructive.isChecked

            if (Prefs.telegramEnabled && Prefs.telegramBotToken.isNotBlank()) {
                TelegramBotManager.start(this)
            } else {
                TelegramBotManager.stop()
            }

            // Warn if no API key is set
            if (apiKey.isBlank()) {
                Toast.makeText(this, "Settings saved, but no API key is set!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
