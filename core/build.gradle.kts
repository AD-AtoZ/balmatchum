import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// 판단 로직만 담은 순수 Kotlin 모듈. 안드로이드 없이 ./gradlew :core:test 로 시험한다.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    // JsonElement 트리만 쓰므로 serialization 컴파일러 플러그인은 필요 없다.
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
