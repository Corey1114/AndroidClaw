# AndroidClaw 🤖

**OpenClaw for Android. AI Agent. One APK. No laptop needed.**

Give your Android phone a goal in plain English — it reads the screen, thinks, taps, types, and gets it done.

---

## What it does

AndroidClaw turns your Android phone into a fully autonomous AI agent:

- **Reads any screen** using Android's Accessibility API (no screenshots, no OCR)
- **Reasons with LLM** — Gemini, Groq, OpenRouter, or any OpenAI-compatible model
- **Executes actions** — taps, types, scrolls, opens apps, fills forms
- **Runs 24/7** as a foreground service, survives reboots
- **Receives commands via Telegram** — control your phone from anywhere
- **Voice input** — speak your goal using Android's built-in speech recognition
- **Scheduled tasks** (cron) — runs tasks automatically at set times
- **Extensible via SKILL.md files** — same format as OpenClaw

---

## Setup: 2 minutes, 4 steps

1. **Install APK** — download from Releases
2. **Pick your LLM** — Gemini (free), Groq (free), or Pollinations (zero key)
3. **Connect Telegram** (optional) — for remote control
4. **Enable Accessibility** — one toggle in Settings

That's it. No terminal. No config files. No laptop.

---

## Supported LLM Providers

| Provider | Free? | Key Needed? | Best For |
|---|---|---|---|
| Google Gemini | ✅ Free | Yes (aistudio.google.com) | Best quality free |
| Groq | ✅ Free tier | Yes (console.groq.com) | Fastest |
| Pollinations AI | ✅ Completely free | ❌ No key | Zero setup |
| OpenRouter | ✅ Free models | Yes | Model variety |
| GitHub Models | ✅ Free | GitHub token | GPT-4o free |
| OpenAI | Paid | Yes | GPT-4o/4.1 |
| Custom/Local | ✅ Free | Optional | Ollama, LMStudio |

---

## Example Commands

Send these via Telegram or type in the app:

```
"Open WhatsApp and send Rahul: Meeting at 5pm"
"Search Google for tiffin services in Wakad Pune and list the top 5"
"Open Settings and turn on Do Not Disturb"
"Take a screenshot and tell me what's on screen"
"Open YouTube and search for n8n automation tutorial"
"Check my Gmail for any emails from clients today"
```

---

## Skills (SKILL.md)

Drop any `.md` file into `AndroidClaw/skills/` on your phone to teach new capabilities.

Built-in skills:
- `whatsapp.md` — WhatsApp messaging
- `google_search.md` — Google search and results

Create your own:
```markdown
# My Custom Skill
> What this skill does
Trigger: keyword that activates this skill

## Instructions
Step by step what the agent should do...
```

---

## Security

- **AES-256-GCM** — all API keys and memory encrypted locally
- **Android Keystore** — encryption keys stored in hardware security module
- **Zero telemetry** — screen data never leaves your device
- **Post-quantum ready** — Bouncy Castle 1.77 included (ML-KEM in v2)
- **Request signing** — HMAC-SHA256 on all API calls

---

## Architecture

```
┌─────────────────────────────────┐
│  GATEWAY: Telegram + Voice + UI │
├─────────────────────────────────┤
│  BRAIN: ReAct Loop (Kotlin)     │
│  LLM → Think → Act → Observe   │
├─────────────────────────────────┤
│  PERCEPTION: Accessibility XML  │
│  Reads every screen as HTML     │
├─────────────────────────────────┤
│  EXECUTION: Accessibility API   │
│  Tap, Type, Scroll, Open Apps  │
├─────────────────────────────────┤
│  MEMORY: Encrypted Markdown     │
│  SKILLS: SKILL.md files         │
│  CRON: WorkManager scheduler    │
├─────────────────────────────────┤
│  SECURITY: AES-256-GCM + PQ     │
└─────────────────────────────────┘
```

---

## Build from Source

```bash
git clone https://github.com/hshant966/AndroidClaw
cd AndroidClaw
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

Requirements: JDK 17+, Android SDK 34

GitHub Actions builds APK automatically on every push.

---

## Roadmap

- [x] v1: ReAct loop + Accessibility + Telegram + Voice + Setup wizard
- [ ] v2: ML-KEM post-quantum network encryption
- [ ] v2: Local LLM (Llama 3B via llama.cpp on-device)
- [ ] v2: Floating overlay button
- [ ] v3: Multi-device agent mesh
- [ ] v3: Skill marketplace

---

## Inspired by

- [OpenClaw](https://github.com/karakuriagent/openclaw) — the original desktop agent
- [iClaw](https://github.com/boredrhino/iclaw) — iPhone agent (proved the concept)
- [DroidClaw](https://github.com/unitedbyai/droidclaw) — early Android attempts

**AndroidClaw is the complete, packaged Android version that didn't exist.**

---

Built by [ads4you](https://hshant966.github.io/ads4you) — AI Automation Studio, Pune 🇮🇳

*New business. Real skills. Honest work.*
