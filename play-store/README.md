# DayStart — Play Store pack

Everything you need to submit `com.loudwake.app` besides the Play developer account ($25) and phone screenshots.

| Item | Status | Where |
|------|--------|--------|
| Privacy policy (live) | Ready | https://daystart-privacy.pages.dev/ |
| Release signing | Ready | `SIGNING.md` + local `keystore.properties` |
| Release AAB | Built | `app/build/outputs/bundle/release/app-release.aab` |
| Data safety / rating / declarations | Copy-paste answers | `CONSOLE_FORMS.md` |
| Store listing text + graphics | Ready | `STORE_LISTING.md` + `assets/` |
| Phone screenshots | Ready (4) | `assets/screenshots/` |
| Go-live checklist | Ready | `GO_LIVE.md` |

## Battery / policy UX in the app

- Unrestricted-battery is **not** forced on launch.
- User sees a clear rationale dialog first (“Allow for reliable alarms” / “Not now”).
- Home shows a soft banner if battery saver may still block alarms.
- Privacy policy + reviewer notes state: alarm/clock app; activity & mic on-device only; never uploaded.
