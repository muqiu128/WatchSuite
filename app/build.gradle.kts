plugins {
    id("com.android.application")
}

android {
    namespace = "com.watchsuite"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.watchsuite"
        minSdk = 26
        targetSdk = 30
        versionCode = 19
        versionName = "1.19"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java")
        }
    }
}
