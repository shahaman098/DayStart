# Readiness pass — documented code/doc changes

Date: 23 July 2026

## Privacy / store docs
- `privacy-site/index.html` — disclose microphone + Voice set / speech recognition; soften “entirely on-device”; contact email.
- `STORE_LISTING.md` — accurate offline claims; mic in reviewer notes.
- `CONSOLE_FORMS.md` — declare Microphone (+ Photos/Activity guidance).

## App code
- `AndroidManifest.xml` — drop `SCHEDULE_EXACT_ALARM`; keep `USE_EXACT_ALARM`.
- `DayStartService.kt` — stop foreground when muted / gate-quiet (no mediaPlayback FGS without audio).
- `MainActivity.kt` — About/Privacy; lazy permissions; phase CTA; listening Cancel; empty state; FAB a11y + icons; list padding.
- `Theme.kt` — `dynamicColor` default `false`.
- `res/drawable/ic_stat_alarm.xml` — white notification icon; wired in `DayStartService`.
- `res/xml/file_paths.xml` — restrict FileProvider to `challenge_photos/`.
- `res/values/strings.xml` — privacy URL, support email, about/credits strings.

## Not done in this pass (still blocks “ready”)
- Redeploy privacy Pages site.
- Capture Relapse + Wake Gate screenshots.
- Rebuild signed AAB + release smoke.
- Adaptive icon safe-zone redesign.
