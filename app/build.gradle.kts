plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.maeilmail.app"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.maeilmail.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "0.7.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 개인용: 별도 키스토어 없이 debug 키로 서명해 바로 설치할 수 있게 한다
            signingConfig = signingConfigs.getByName("debug")
        }
        // 지금 쓰는 앱과 나란히 설치해 써 보는 판. 한 휴대폰에서 같은 패키지는 한 판만 둘 수 있어(듀얼 앱도 코드를 함께 쓴다)
        // 패키지 이름과 앱 이름만 다르게 한다
        create("preview") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".preview"
            matchingFallbacks += "debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        // 설정 화면에 앱 버전을 보인다
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
}
