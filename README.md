# MobileAgent — Native Kotlin Android Automation Agent

An open-source Android automation agent built with **native Kotlin** (not Flutter).
Uses Accessibility Services + any OpenAI-compatible LLM to interpret screen layouts
and execute multi-step tasks via natural language commands.

## Architecture

1. **AccessibilityService** captures the screen's UI tree (interactive elements + coordinates)
2. Screen data is formatted into a text prompt and sent to an LLM
3. The LLM returns a structured JSON action (tap, type, scroll, press button, done)
4. The action is executed via gesture dispatch / accessibility APIs
5. The loop repeats until the task is marked complete

## Features

- **Screen Reading**: Parses the Android UI tree to map all interactive elements
- **Coordinate-Based Interaction**: Simulates taps, long presses, and swipes
- **Text Input**: Types into focused editable fields via AccessibilityNodeInfo
- **System Buttons**: Back, Home, Recents via global actions
- **Voice Control**: Native Android SpeechRecognizer for hands-free operation
- **Telegram Remote Control**: Issue tasks remotely via Telegram Bot API
- **Destructive Action Guard**: Optional confirmation before payments/deletions
- **Any LLM Provider**: Works with OpenRouter (free models), DeepSeek, Ollama, or any OpenAI-compatible API

## Setup (Free with OpenRouter)

1. Build the APK: `./gradlew assembleDebug`
2. Install on your Android device (API 26+)
3. Go to [openrouter.ai](https://openrouter.ai), create account, get free API key
4. Open MobileAgent > Settings > tap "OpenRouter" chip > paste API key
5. Enable "MobileAgent Screen Control" in Accessibility Settings
6. If blocked: App Info > Allow restricted settings > re-enable accessibility

## Building

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (needs signing config)
./gradlew assembleRelease
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Supported LLM Providers

| Provider   | Base URL                            | Free Models |
|------------|--------------------------------------|-------------|
| OpenRouter | https://openrouter.ai/api/v1        | Yes         |
| DeepSeek   | https://api.deepseek.com/v1         | Limited     |
| Ollama     | http://localhost:11434/v1           | Yes (local) |
| Any OpenAI | Custom                               | Depends     |

## Telegram Remote Control

1. Create a bot via @BotFather on Telegram
2. Paste the bot token in Settings > Telegram
3. Enable the toggle
4. Send any task as a message to your bot

## Project Structure

```
app/src/main/java/com/mobileagent/
├── AgentAccessibilityService.kt   # Screen tree capture + gesture dispatch
├── AgentForegroundService.kt      # Keeps process alive during tasks
├── MobileAgentApp.kt              # Application class
├── agent/
│   ├── AgentAction.kt             # Sealed class of all action types
│   ├── AgentLoop.kt               # Core loop: capture → LLM → execute → repeat
│   ├── ActionExecutor.kt          # Executes actions via accessibility/gestures
│   └── ScreenCapture.kt           # Walks UI tree, extracts interactive elements
├── data/
│   ├── AgentLog.kt                # Log entry model
│   └── Prefs.kt                   # SharedPreferences wrapper
├── llm/
│   └── LlmClient.kt               # OpenAI-compatible API client
├── telegram/
│   └── TelegramBotManager.kt      # Telegram long-polling bot
├── ui/
│   ├── MainActivity.kt            # Task input + status
│   ├── SettingsActivity.kt        # API provider config
│   ├── AgentControlActivity.kt    # Live agent log viewer
│   └── LogAdapter.kt              # RecyclerView adapter for logs
└── voice/
    └── VoiceInputHelper.kt        # Native STT wrapper
```

## License

Open source. Modify freely.