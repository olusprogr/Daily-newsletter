plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.olusprogr.schacht"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.olusprogr.schacht"
        minSdk = 24
        targetSdk = 34
        versionCode = 74
        versionName = "0.56-hightech"
    }

    signingConfigs {
        // Fester Schluessel im Repo, damit JEDER Build dieselbe Signatur hat.
        // Nur so lassen sich neue Versionen ohne Deinstallation (und damit ohne
        // Verlust des Spielstands) ueber die alte druebersinstallieren.
        create("stable") {
            storeFile = file("schacht.jks")
            storePassword = "schacht123"
            keyAlias = "schacht"
            keyPassword = "schacht123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stable")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stable")
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
    implementation("androidx.activity:activity-ktx:1.8.2")
}
