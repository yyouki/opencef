plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.opencef.android"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core"))

    // src/test-only: plain JUnit4 for PageGenerationTrackerTest, a pure
    // JVM logic test with zero Android dependency - does not need the
    // instrumentation runner below at all.
    testImplementation(libs.junit)

    // androidTest-only: the minimal runner/context infrastructure an
    // Android instrumented test needs to exist at all
    // (SamePageNavigationInstrumentedTest below). Not used by the
    // framework itself - main/ has no androidx.test dependency.
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core)
}
