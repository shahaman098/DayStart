# DayStart — Play Console form answers

Fill these in Google Play Console while creating the app. Values match the current codebase (`com.loudwake.app`).

---

## 1. App access / category

| Field | Answer |
|-------|--------|
| App name | DayStart |
| Default language | English (United States) — change if you prefer |
| App or game | App |
| Free or paid | Free (or Paid — your choice) |
| Category | **Clock & Alarm** (preferred) / Productivity |
| Tags | alarm, wake up, morning, clock |

Declare in **App content → Sensitive permissions / declarations** (wording varies by Console version):

- This is an **alarm or clock app** whose core purpose is wake-up alarms.
- Exact alarms are required for scheduled wake times.
- Full-screen intent is required to present the alarm over the lock screen.

---

## 2. Data safety form

### Overview

| Question | Answer |
|----------|--------|
| Does your app collect or share any of the required user data types? | **Yes** for on-device / optional feature data below. DayStart does **not** share data to a DayStart server. |
| Is all user data encrypted in transit? | N/A for DayStart servers (none). Optional Voice set may use Google speech services. |
| Do you provide a way for users to request data deletion? | Users can clear app storage / uninstall. |

**Declare these (safer + honest):**

**Physical activity**

| Field | Answer |
|-------|--------|
| Collected | Yes (on device) |
| Shared | No |
| Required or optional | Optional |
| Purpose | App functionality |
| Notes | Movement challenges during Wake Gate; not sent to DayStart |

**Microphone / audio**

| Field | Answer |
|-------|--------|
| Collected | Yes (for optional Voice set) |
| Shared | No by DayStart (system / Google speech recognizer may process audio per device settings) |
| Required or optional | Optional |
| Purpose | App functionality |
| Notes | Voice set creates alarms from spoken dates/times; DayStart keeps the transcript locally as alarm settings |

**Do not declare:** location, personal info, financial info, messages, files/docs, photos/videos, web browsing, app activity analytics, advertising ID.

### Privacy policy

Paste the public URL from `play-store/PRIVACY_URL.txt` after deploy.

---

## 3. Content rating (IARC questionnaire)

Answer for a utility alarm app with optional movement/voice challenges (no UGC feed, no social, no ads):

| Topic | Typical answer |
|-------|----------------|
| Violence | No |
| Sexual content | No |
| Profanity | No (or Mild if roast voice lines count — check in-app copy; if jokes are mild, No/Mild) |
| Controlled substances | No |
| Soft misappropriation / crime | No |
| User interaction | Users interact only with their own device; no user-to-user sharing |
| Share location | No |
| Personal info shared | No |
| Users can interact online | No |
| Age | Expected result: **Everyone** or **PEGI 3** / similar |

If Wake Roasts include strong language, answer Profanity honestly (may raise rating slightly).

---

## 4. Restricted permissions declarations

### Exact alarm (`USE_EXACT_ALARM`)

- Core functionality: **Yes — alarm clock**
- Use: schedule wake-up alarms at a precise time via `AlarmManager` / `setAlarmClock`
- User-visible: alarms the user configured

### Full-screen intent (`USE_FULL_SCREEN_INTENT`)

- Purpose: show alarm / Wake Gate / Relapse Guard when the device is locked or another app is open
- Acceptable for alarm apps

### Foreground service — media playback

- Plays alarm audio during an active wake session
- Type declared: `mediaPlayback`

### Battery optimization (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)

- Optional; shown only after an **in-app rationale dialog**
- User can choose **Not now**
- Justification: prevent OEM battery savers from delaying wake alarms and Relapse Guard
- Not used for ads, tracking, or unrelated background work

### Camera

- Optional Wake Gate photo challenges
- On-device ML Kit labeling only
- Never uploaded

### Microphone

- Optional voice set / speech recognition
- Uses the device speech recognizer (may involve Google’s service on some devices)
- DayStart does not operate its own upload backend for recordings

### Activity recognition

- Optional movement challenges
- On-device only

---

## 5. Ads / Target audience / News / COVID / Data safety ads

| Form | Answer |
|------|--------|
| Ads | **No** (app contains no ads) |
| Target audience | 18+ or all ages utility — not primarily children; if asked “appeal to children” → No |
| News app | No |
| COVID-19 contact tracing / status | No |
| Financial features | No |
| Health features | No (not a medical device; lifestyle alarm only) |

---

## 6. Store listing contact

Set in Console (also used by the privacy policy page):

- Email: amanshah0844@gmail.com  

- Phone: optional  
- Website: privacy URL or a simple landing page  

---

## 7. Countries / pricing

Start with your country + major markets; expand later. No in-app products required for v1.
