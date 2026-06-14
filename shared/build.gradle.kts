plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.materialIconsExtended)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.gson)
            implementation(libs.okhttp)
            implementation(libs.okhttp.sse)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.ktx)
            implementation(libs.room.runtime)
            implementation(libs.room.ktx)
            implementation(libs.coil.compose)
            implementation(libs.kotlinx.coroutines.android)
            implementation("androidx.security:security-crypto:1.1.0-alpha06")
            implementation(project(":llama-android-lib"))
        }
        commonTest.dependencies {
            implementation(libs.junit)
        }
    }
}

android {
    namespace = "com.bytedace.doubaoapp.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 33
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
}
