import java.util.Properties

plugins{
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

// 개인 PC에만 두는 값(API 키 등)은 Git에 올리지 않는 local.properties에서 읽는다
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.example.viewit"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.viewit"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 코드에서는 BuildConfig.YOUTUBE_API_KEY로 사용. 값이 없으면 빈 문자열
        buildConfigField(
            "String",
            "YOUTUBE_API_KEY",
            "\"${localProperties.getProperty("YOUTUBE_API_KEY", "")}\""
        )
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    viewBinding {
        enable = true
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    implementation("com.google.firebase:firebase-auth")

    implementation("com.google.firebase:firebase-firestore")

    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // 썸네일 이미지 불러오기·캐시 (4.1)
    implementation("com.github.bumptech.glide:glide:5.0.9")
}
