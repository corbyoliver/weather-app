import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Secrets live in local.properties, which is gitignored (this repo is public).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localProp(name: String): String = localProps.getProperty(name, "")
fun quoted(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "dev.corbyoliver.weather"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.corbyoliver.weather"
        // The app is for one Android 16 phone. See CLAUDE.md, "Decisions".
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "PIRATE_WEATHER_API_KEY", quoted(localProp("pirateWeatherApiKey")))
        buildConfigField("String", "NWS_USER_AGENT", quoted(localProp("nwsUserAgent")))
    }

    // The personal key from scripts/create-signing-key.sh signs every build,
    // so the phone accepts updates. Without it, Android's default debug key
    // is used (fine for the emulator, but tied to this Mac).
    val personalKey = localProp("signingStoreFile").takeIf { it.isNotBlank() && file(it).exists() }
    signingConfigs {
        if (personalKey != null) {
            create("personal") {
                storeFile = file(personalKey)
                storePassword = localProp("signingStorePassword")
                keyAlias = localProp("signingKeyAlias")
                keyPassword = localProp("signingStorePassword")
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        debug {
            if (personalKey != null) signingConfig = signingConfigs.getByName("personal")
        }
        release {
            isMinifyEnabled = false
            if (personalKey != null) signingConfig = signingConfigs.getByName("personal")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The DM Sans licence (SIL OFL) must travel with the font: package the
    // repo's licenses/ folder as APK assets.
    sourceSets {
        getByName("main").assets.directories.add("../licenses")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("io.ktor:ktor-client-core:3.6.1")
    implementation("io.ktor:ktor-client-okhttp:3.6.1")
    implementation("androidx.datastore:datastore:1.3.0")
    implementation("androidx.work:work-runtime:2.12.0")
    implementation("androidx.glance:glance-appwidget:1.2.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
