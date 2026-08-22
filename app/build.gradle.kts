import java.util.Properties

plugins {
    id("com.android.application")
}

// Names release artifacts roamer-release.apk (instead of app-release.apk)
// so the release script can attach a predictable file to GitHub Releases.
base.archivesName = "roamer"

// Release signing credentials live outside version control: keystore.properties (which
// is gitignored) for local builds, environment variables for CI. Keeping them out of the
// build script means publishing the repository never publishes the signing key.
//
// Signing is configured only when a keystore and password are both present. Debug builds
// and unit tests deliberately require neither, so a fresh clone builds and tests with no
// secrets at all.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingSetting(propertyKey: String, environmentKey: String): String? =
    keystoreProperties.getProperty(propertyKey) ?: System.getenv(environmentKey)

val releaseKeystore = rootProject.file("keystore/release.keystore")
val releaseStorePassword = signingSetting("storePassword", "ROAMER_KEYSTORE_PASSWORD")
val releaseKeyPassword =
    signingSetting("keyPassword", "ROAMER_KEY_PASSWORD") ?: releaseStorePassword
val releaseKeyAlias = signingSetting("keyAlias", "ROAMER_KEY_ALIAS") ?: "roamer"
val canSignRelease = releaseKeystore.exists() && !releaseStorePassword.isNullOrBlank()

android {
    namespace = "si.merhar.roamer"
    compileSdk = 37

    defaultConfig {
        applicationId = "si.merhar.roamer"
        minSdk = 29
        targetSdk = 37
        versionCode = 3
        versionName = "1.1"
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            // Left unsigned when no credentials are available. bin/release.sh verifies
            // the APK is signed before publishing, so an unsigned build cannot ship.
            signingConfig = if (canSignRelease) signingConfigs.getByName("release") else null
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all {
            // Part of the suite asserts through kotlin.assert, which the JVM evaluates only
            // with assertions enabled. Gradle enables them by default; stating it explicitly
            // keeps those tests from silently becoming no-ops if that default ever changes.
            it.enableAssertions = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.2.0")
}
