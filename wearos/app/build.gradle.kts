plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ssafy.woojuin"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.ssafy.woojuin"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // 개발·데모는 dev 서버를 향한다. release 가 prod 로 덮어쓴다.
        // 로컬 백엔드 실기기 테스트: gradlew installDebug "-PapiBaseUrl=http://<PC IP>:8080/api"
        // (README 참고 — debug 는 cleartext 허용이라 http 로컬 주소가 통한다)
        val apiBaseUrl = providers.gradleProperty("apiBaseUrl")
            .getOrElse("https://api.dev.woojuin.store/api")
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            buildConfigField("String", "API_BASE_URL", "\"https://api.woojuin.store/api\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    useLibrary("wear-sdk")
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.navigation)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.material.icons.extended)
    implementation(libs.wear.tiles)
    implementation(libs.protolayout)
    implementation(libs.protolayout.material)
    implementation(libs.protolayout.expression)
    implementation(libs.watchface.complications.data.source.ktx)
    implementation(libs.concurrent.futures.ktx)
    implementation(libs.compose.ui.tooling)
    implementation(libs.core.splashscreen)
    implementation(libs.play.services.wearable)
    implementation(libs.play.services.location)
    implementation(libs.okhttp)
    implementation(libs.datastore.preferences)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.wear.tooling.preview)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.test.manifest)
    debugImplementation(libs.ui.tooling)
}