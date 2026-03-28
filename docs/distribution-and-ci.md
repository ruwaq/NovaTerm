# NovaTerm Distribution, CI/CD, and Release Strategy
**Date:** March 2026 | **Author:** NovaTerm Research

---

## Distribution Channels (Priority Order)

1. **GitHub Releases** — Immediate, no friction, APK signed with our key
2. **IzzyOnDroid** — Fast F-Droid-compatible repo, days not weeks
3. **F-Droid** — Primary for OSS audience, needs metadata YAML
4. **Google Play Store** — Maximum reach, requires AAB + review
5. **Accrescent** — Security-focused store, low extra effort
6. **Obtainium** — Works automatically if we publish on GitHub Releases

## App Signing Strategy

```
Key A (upload key) → Play Store (Google re-signs)
Key B (self-signing) → GitHub Releases, F-Droid, IzzyOnDroid
```

- Never commit keystore to repo
- Store as GitHub Secret (base64 encoded)
- Play Store and F-Droid APKs have DIFFERENT signatures (users can't switch)
- Document this clearly for users

## CI/CD (GitHub Actions)

### Build + Test on every PR:
- JDK 21 + Gradle 9.4.1 + NDK 29
- `./gradlew assembleDebug testDebugUnitTest`
- Upload APK as artifact

### Release on tag:
- Build signed release APK
- Create GitHub Release with APK + release notes
- Optionally publish to Play Store via Triple-T plugin

### Future Rust builds:
- `rustup target add aarch64-linux-android`
- `cargo ndk -t arm64-v8a build --release`
- Rust cache via `Swatinem/rust-cache@v2`

## Build Flavors

```kotlin
flavorDimensions += "distribution"
productFlavors {
    create("foss") { /* No proprietary deps */ }
    create("play") { /* With Play Core for in-app updates */ }
}
```

## Play Store Policies

- `specialUse` FGS: detailed PROPERTY_SPECIAL_USE_FGS_SUBTYPE + Play Console declaration
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: may be rejected, have manual fallback
- targetSdk 36 required by August 2026
- arm64-only is fine (app hidden on incompatible devices)

## F-Droid Metadata

File: `metadata/com.novaterm.app.yml` in fdroiddata repo
- AutoUpdateMode: Version (detects tags automatically)
- Build cycle: 3-7 days from tag to availability
- Reproducible builds: possible but complex (not required for inclusion)
