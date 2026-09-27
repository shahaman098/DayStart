# DayStart — go-live checklist (after Google verifies you)

Account: `amanshah0844@gmail.com`  
Identity review must finish first, then phone verify, then create the app.

## Already prepared in this repo

| Item | Path / URL |
|------|------------|
| Signed AAB (rebuild after code changes) | `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab` |
| Privacy policy | https://daystart-privacy.pages.dev/ |
| Listing copy | `STORE_LISTING.md` |
| Console answers | `CONSOLE_FORMS.md` |
| Icon + feature graphic | `assets/play_icon_512.png`, `assets/feature_graphic_1024x500.png` |
| Screenshots | `assets/screenshots/` (4 phone shots) |
| Upload keystore backup | `~/Documents/DayStart-Play-Backup/` |

## Same-day deploy steps

1. Finish **phone verification** in Play Console (unlocked after identity approval).
2. **Create app** → `DayStart` / `com.loudwake.app` / Free / **Clock & Alarm**.
3. Paste privacy URL + store listing + upload graphics/screenshots.
4. Complete Data safety / content rating / declarations from `CONSOLE_FORMS.md`.
5. Upload **fresh** `app-release.aab` (JDK 21):  
   `export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home" && ./gradlew bundleRelease`
6. Start with **internal testing** track → install on your phone → run smoke test below.
7. Promote to production when smoke test passes.

## Smoke test (15 minutes)

- [ ] Fresh install: default Morning alarm is **off** (no surprise 7:00 ring)
- [ ] Enable alarm a few minutes ahead; exact-alarm / notification prompts OK
- [ ] Battery dialog explains reliability; **Not now** works
- [ ] Alarm rings; Wake Gate opens; math/steps/memory/shake/phrase challenges work
- [ ] “Stop this ring” → Chase; after ~3 chase rounds session **auto-ends** (no forever drain)
- [ ] Emergency stop phrase works from the active-session card
- [ ] Reboot phone; enabled alarm still scheduled
- [ ] Idle overnight with alarms set: no sticky notification / no FGS when session idle

## Battery stance (what reviewers / users should expect)

- **Idle:** only `AlarmManager.setAlarmClock` — negligible drain.
- **Ringing / chase:** intentional high use (audio + FGS) until Wake Gate clears or chase cap.
- Unrestricted battery is **optional** and rationale-gated.

## Competitive bar (honest)

Ship-ready as a focused alarm/wake product: clear brand, challenge modes, Relapse Guard, on-device photo check, polished Material UI. Not feature-parity with Alarmy on every OEM quirk — validate on your Oppo/Pixel before production.
