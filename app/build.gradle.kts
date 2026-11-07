plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val runNo = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
android {
    namespace = "com.kakario.mihome"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.kakario.mihome"
        minSdk = 26
        targetSdk = 34
        versionCode = runNo
        versionName = "1.0.$runNo"
    }
    signingConfigs {
        create("rel") {
            storeFile = file("release.jks")
            storePassword = "mihome123"
            keyAlias = "mihome"
            keyPassword = "mihome123"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("rel")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { checkReleaseBuilds = false; abortOnError = false }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
