# Android release gate

Every GitHub APK release must pass this sequence. A successful Gradle build alone is not a release gate.

1. Run `./gradlew clean test assembleDebug`.
2. Run `scripts/verify-apk.sh app/build/outputs/apk/debug/app-debug.apk` with `ANDROID_SDK_ROOT` and `JAVA_HOME` set.
3. Upload that exact file as the release asset.
4. Download the asset from GitHub into a clean temporary directory.
5. Run `scripts/verify-apk.sh` against the downloaded asset.
6. Compare the local and downloaded SHA-256 digests; they must be identical before announcing the release.
