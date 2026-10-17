import java.io.File
import java.security.KeyStore
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.kitt.reader"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.kitt.reader"
        minSdk = 26
        targetSdk = 35
        versionCode = 20
        versionName = "0.6.0"
        testInstrumentationRunner = "com.kitt.reader.HotfixPhoneProbe"
        // The offline recogniser ships native libraries; x86 ABIs would double the APK without serving any phone.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    // Project-specific acceptance signing: identical private key on office and home PCs.
    // An absent key still permits local unit-test/debug compilation, but acceptance scripts
    // must fail closed. Do not substitute a newly generated ~/.android/debug.keystore.
    signingConfigs {
        getByName("debug") {
            val canonical = File(System.getProperty("user.home"), ".kitt/signing/acceptance.keystore")
            if (canonical.isFile) {
                val keyStore = KeyStore.getInstance(canonical, "android".toCharArray())
                check(keyStore.entryInstanceOf("androiddebugkey", KeyStore.PrivateKeyEntry::class.java)) {
                    "KITT signing key must contain the original private key."
                }
                val certificate = keyStore.getCertificate("androiddebugkey")
                val fingerprint = MessageDigest.getInstance("SHA-256")
                    .digest(certificate.encoded).joinToString("") { "%02x".format(it) }
                check(fingerprint == "3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba") {
                    "KITT signing certificate mismatch. Restore the original acceptance key; do not generate a replacement."
                }
                storeFile = canonical
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Compose's cached main dispatcher otherwise retains a prior Robolectric application's Looper.
        unitTests.all { it.forkEvery = 1 }
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // Native call cancellation bounds real-device socket stalls, including response headers.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Offline Simplified Chinese recogniser, used only when the device's own recogniser cannot work.
    implementation("com.alphacephei:vosk-android:0.3.75")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    // D1 release format/signature tooling. Test-only: nothing of it is packaged into the APK.
    testImplementation(project(":packtool"))
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

/** D1 command line (`build`, `verify`, ...). Runs the JVM unit-test classpath, so the tool can reuse
 *  the D0 county reader without putting a byte of signing code into the shipped application. */
tasks.register<JavaExec>("d1Pack") {
    group = "kitt"
    description = "D1/D2 pack tooling (docs/COUNTY_PACK_D2_PRODUCTION.md)."
    dependsOn("compileDebugUnitTestKotlin", "compileDebugUnitTestJavaWithJavac")
    classpath = configurations.getByName("debugUnitTestRuntimeClasspath") + files(
        layout.buildDirectory.dir("tmp/kotlin-classes/debug"),
        layout.buildDirectory.dir("tmp/kotlin-classes/debugUnitTest"),
        layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes")
    )
    mainClass.set("com.kitt.reader.d1.D1PackCliKt")
    standardInput = System.`in`
    workingDir = rootProject.projectDir
    // A refusal is a normal D1 outcome, so the CLI reports it through status=/exit_code= instead of
    // turning an expected negative case into a Gradle failure.
    isIgnoreExitValue = true
    val flags = listOf("command", "root", "sample", "out", "keydir", "pack", "commit", "appcode",
        "json", "offset", "keyid", "sequence", "minsequence", "area", "parent", "input", "scope",
        "factversion", "narrativeversion", "confirmation", "reviewhash")
    args = flags.mapNotNull { flag -> (project.findProperty("d1.$flag") as String?)?.let { listOf("--$flag", it) } }.flatten()
}
