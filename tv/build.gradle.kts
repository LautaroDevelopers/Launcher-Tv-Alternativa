plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "com.televisionalternativa.launcher"
    compileSdk = 35

    signingConfigs {
        create("release") {
            storeFile = file("../tv-alternativa-release.jks")
            storePassword = "Telev#Alter26#"
            keyAlias = "tv-alternativa"
            keyPassword = "Telev#Alter26#"
        }
    }

    defaultConfig {
        applicationId = "com.televisionalternativa.launcher"
        minSdk = 21
        targetSdk = 35
        versionCode = 4
        versionName = "1.1.2"

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.leanback)
    implementation(libs.glide)
    implementation("androidx.palette:palette-ktx:1.0.0")
}
