plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.appfactory.tidytv.home"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.appfactory.tidytv.home"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The TidyTV build script re-signs this with the release key; the debug key keeps it installable here.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = false }
}

dependencies {
    testImplementation(libs.junit)
}
