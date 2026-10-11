# ZENITH (v0.1) — private Android assistant, starter build

## Build the APK with GitHub
1. Create a new GitHub repo, upload ALL files from this folder (keep the `.github` folder).
2. Open the repo's **Actions** tab -> "Build ZENITH APK" (it also runs on every push; or press "Run workflow").
3. When it finishes, open the run -> **Artifacts** -> download `zenith-debug-apk` -> unzip -> install `app-debug.apk`
   (allow "install unknown apps" on your phone).
4. If the build fails, open the failed step's log and send me the error text.

## What works in this version (UNTESTED until you build it)
- Chat history + saved memories stored in on-device SQLite (survive restarts)
- Commands in English / Hindi / Marathi: call <name>, open <app>, search YouTube for <x>,
  "remember that ...", "what do you remember", "what did we discuss yesterday", "delete all data"
- Calls and data deletion ALWAYS need an explicit "yes"; unclear/no answer = cancelled; multiple contacts -> asks which
- Mic input (Android speech engine; hi-IN / mr-IN may need internet or a language pack on your phone) + spoken replies
- No INTERNET permission in the manifest; no analytics; no cloud AI

## NOT built yet (honest list)
- Local LLM (llama.cpp): `LlmEngine` is the plug-in point. Until then, free chat replies "no model installed".
- Wake word ("Hey Zenith") + background service
- Incoming-call "should I answer?" prompt
- Notification reading / message replies
- Accessibility-based Instagram/YouTube actions (follow, like, scroll)
- Semantic memory search, memory edit/export screens
Add these one stage at a time.
