# KITT V0 build handoff

Work in progress. Repository cloned from shimao1115/KITT; package/application ID com.kitt.reader.

Toolchain: JDK 17, Gradle 8.9, AGP 8.7.3, Kotlin/Compose 2.0.21, Android SDK 35, min Android 8 (26).
Build: `./gradlew.bat assembleDebug testDebugUnitTest` (JAVA_HOME must point to JDK 17).
No connected Android device at bootstrap. Physical GPS, microphone, speaker and background restrictions require device acceptance.
No keys will be embedded in the APK or committed.
