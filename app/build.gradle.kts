plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Names release artifacts roamer-release.apk (instead of app-release.apk)
// so the release script can attach a predictable file to GitHub Releases.
base.archivesName = "roamer"

android {
    namespace = "si.merhar.roamer"
    compileSdk = 35

    defaultConfig {
        applicationId = "si.merhar.roamer"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/release.keystore")
            storePassword = "roamer"
            keyAlias = "roamer"
            keyPassword = "roamer"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.getByName("release")
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

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.20")
}
