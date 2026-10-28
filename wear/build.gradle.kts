import java.util.Properties

// TMAP 키를 BuildConfig.TMAP_APP_KEY로 넣는다. 찾는 순서:
// secrets.properties(개인용, GitHub 제외) → local.properties → secrets.defaults.properties(팀 공용, GitHub 포함)
// 저장소만 받으면 팀 공용 키로 바로 빌드된다.
val tmapAppKey: String = listOf("secrets.properties", "local.properties", "secrets.defaults.properties")
    .map { rootProject.file(it) }
    .filter { it.exists() }
    .firstNotNullOfOrNull { file ->
        Properties().apply { file.inputStream().use { load(it) } }.getProperty("TMAP_APP_KEY")?.trim()?.takeIf { it.isNotEmpty() }
    } ?: ""

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "kr.ac.cnu.balmatchum"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "kr.ac.cnu.balmatchum"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "TMAP_APP_KEY", "\"$tmapAppKey\"")

    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
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
    implementation(project(":core"))
    implementation(libs.play.services.location)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.wear.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling)
    implementation(libs.play.services.wearable)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}