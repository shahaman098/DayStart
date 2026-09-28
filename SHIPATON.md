# RevenueCat Shipaton 2026 (Next Gen)

Entry: **DayStart** (`com.loudwake.app`) · track **Next Gen Award**.

## RevenueCat setup

1. Create a RevenueCat project and Google Play app for `com.loudwake.app`.
2. Add an entitlement id **`premium`** and a default offering with at least one package.
3. Copy the **public** Google Play SDK key into `local.properties`:

   `revenuecat.public_api_key=goog_...`

4. Build: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew assembleDebug`
5. Run on a device; use a sandbox test account for a test purchase.

## Monetization in the app

- Free: one alarm, **Math** Wake Gate only.
- Premium (RevenueCat entitlement `premium`): unlimited alarms and all Wake Gate modes.

## Devpost

- Event: https://revenuecat-shipaton-2026.devpost.com/
- Deadline: 30 Sep 2026 23:45 PDT (1 Oct 2026 07:45 BST)
- Shipaton assets: `play-store/assets/play_icon_1024.png`, `play-store/assets/screenshots/shipaton_1179x2556.png`
