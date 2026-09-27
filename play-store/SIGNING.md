# Release signing

## What’s already set up

| Item | Location |
|------|----------|
| Upload keystore | `signing/upload-keystore.jks` (gitignored) |
| Passwords / alias | `keystore.properties` (gitignored) |
| Example template | `keystore.properties.example` |
| Gradle wiring | `app/build.gradle.kts` → `signingConfigs.release` |

**Back up** `signing/upload-keystore.jks` and `keystore.properties` somewhere safe (password manager + encrypted drive). If you lose the upload key, Play App Signing recovery is painful.

## Build a Play upload (AAB)

Use JDK 17 or 21 (JDK 26 breaks this Gradle/Kotlin toolchain):

```bash
cd "/Users/efi/Daily Projects/Android Alarm"
export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"
./gradlew bundleRelease
```

Output:

`app/build/outputs/bundle/release/app-release.aab`

Upload that AAB in Play Console → Production / Testing → Create release.

## First-time Play App Signing

1. Create the app in Play Console (`com.loudwake.app`).
2. Opt in to **Play App Signing** (default).
3. Upload the AAB signed with this **upload** key.
4. Google stores the **app signing** key; keep your upload keystore for future updates.
