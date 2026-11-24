plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val runNo = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
val tagName = System.getenv("RELEASE_TAG")?.removePrefix("v") ?: "1.0.$runNo"
val ksFile = System.getenv("KEYSTORE_FILE")
val ksPass = System.getenv("KEYSTORE_PASS")
android {
    namespace = "com.kakario.mihome"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.kakario.mihome"
        minSdk = 26
        targetSdk = 34
        versionCode = runNo
        versionName = tagName
    }
    // 서명 키는 저장소에 두지 않고 GitHub Secrets에서만 받음
    signingConfigs {
        if (ksFile != null && ksPass != null) create("rel") {
            storeFile = file(ksFile)
            storePassword = ksPass
            keyAlias = "mihome"
            keyPassword = ksPass
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("rel")
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
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
