import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

/**
 * The key shake-to-report posts issues with. Never in the repository: `local.properties` is
 * ignored by git, and CI hands it in from a repository secret. An empty string is a working
 * build — reports queue on the phone and go out from a later one that has the key.
 */
val reportToken: String = run {
    val local = rootProject.file("local.properties")
    val fromFile = if (local.exists()) {
        Properties().apply { local.inputStream().use { load(it) } }.getProperty("reportToken")
    } else {
        null
    }
    fromFile ?: System.getenv("REPORT_TOKEN") ?: ""
}

android {
    namespace = "com.gios.lightpass"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.gios.lightpass"
        minSdk = 29
        targetSdk = 35
        versionCode = 12
        versionName = "1.10.0"

        buildConfigField("String", "REPORT_TOKEN", "\"$reportToken\"")
        buildConfigField("String", "REPORT_REPO", "\"gi-os/light-reports\"")
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("../keystore/lightpass.jks")
            storePassword = "lightpass"
            keyAlias = "lightpass"
            keyPassword = "lightpass"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // buildConfig carries REPORT_TOKEN into the app; see the reportToken block above.
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // CameraX
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    // Images
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Networking (Claude Vision)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // QR scanning (key entry)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    // Named explicitly rather than left to the embedded scanner's transitive pull: the readers and
    // writers here are the feature, not an incidental dependency of a QR-scanning activity.
    implementation("com.google.zxing:core:3.5.3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Local unit tests. util/TicketDate.kt and util/BookingCode.kt are deliberately free of
    // android.* so plain JUnit can reach them; nothing here needs Robolectric or a device.
    testImplementation("junit:junit:4.13.2")
}
