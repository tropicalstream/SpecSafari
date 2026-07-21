plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.specsafari"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.specsafari"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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

// Zero external dependencies, zero vendor AARs, zero binary assets:
// every chirp is synthesized at first launch, every sprite is vector code.
dependencies {
    implementation(project(":shared"))
}
