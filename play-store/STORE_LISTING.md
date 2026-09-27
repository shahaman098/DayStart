# DayStart — Google Play store listing

Package: `com.loudwake.app`  
App name: **DayStart**

Privacy policy URL: **https://daystart-privacy.pages.dev/** (also in `PRIVACY_URL.txt`).

## Short description (80 chars max)

```
Alarm clock that makes you finish Wake Gate before the morning sticks.
```

## Full description

```
DayStart is an alarm clock built for people who dismiss and go back to sleep.

Set a wake time. When it rings, you don’t just swipe away the alarm — you finish Wake Gate challenges, then clear Relapse Guard so the morning actually sticks.

WHY DAYSTART
• Real alarm-clock scheduling with exact wake times
• Wake Gate challenges so “dismiss” isn’t the finish line
• Relapse Guard after you clear the gate
• Modes from lighter to stricter wake missions
• Alarms and settings stay on your phone (alarms work offline)
• Optional Voice set to create alarms by speaking (uses your device’s speech recognition)

PRIVACY BY DESIGN
• No account required
• No ads SDK
• Camera challenges (optional) are processed on-device with ML Kit and are never uploaded
• Activity / movement challenges stay on your device
• Microphone is only used for optional Voice set; DayStart does not run its own speech cloud

RELIABILITY
DayStart may ask for notifications, exact alarms, and (optionally) unrestricted battery so Android doesn’t delay your wake-up. These settings exist only to make the alarm clock reliable.

DayStart — wake up for real.
```

## App category

**Clock & Alarm** (or Productivity if Clock isn’t available in your console region)

## Tags / notes for reviewers (App content → “Notes for reviewers”)

```
DayStart is an alarm / clock app. Core functionality is scheduling wake-up alarms with AlarmManager.setAlarmClock, full-screen alarm UI, and post-wake challenges.

USE_EXACT_ALARM: required for wake-up alarms at a precise time (alarm/clock app).
USE_FULL_SCREEN_INTENT: required to show the alarm over the lock screen.
FOREGROUND_SERVICE_MEDIA_PLAYBACK: plays the alarm tone only while audio is actively playing.
REQUEST_IGNORE_BATTERY_OPTIMIZATIONS: optional, user-confirmed dialog explaining that unrestricted battery keeps wake alarms reliable. User can decline.
RECORD_AUDIO: optional Voice set (create alarms by speaking) via system speech recognition.
ACTIVITY_RECOGNITION: optional movement challenges; on-device only.

Test path:
1. Open app → create/enable an alarm a few minutes ahead (or use Test Wake Gate).
2. Allow notifications if prompted.
3. Optionally allow battery unrestricted via the in-app rationale dialog (or skip with Not now).
4. When alarm fires / test starts, complete Wake Gate; Relapse Guard follows.
```

## Graphic assets (this repo)

| Asset | Path | Spec |
|-------|------|------|
| High-res icon | `play-store/assets/play_icon_512.png` | 512×512 |
| Feature graphic | `play-store/assets/feature_graphic_1024x500.png` | 1024×500 |
| Phone screenshots | Capture on device/emulator — see below | 16:9 or 9:16, min 2 |

### Screenshot checklist (capture yourself)

Use a phone or emulator (API 34+), light sunrise theme:

1. Home — next wake time + alarm list  
2. Alarm editor — time + mode  
3. Wake Gate challenge screen (test mode)  
4. Relapse Guard screen  
5. (Optional) Exact-alarm / battery fix banners when permissions are missing  

Save PNGs into `play-store/assets/screenshots/` (create the folder). Play requires at least 2 phone screenshots.
