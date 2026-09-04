plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.daumo.ads"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")

    // For ADS
    implementation("com.google.android.gms:play-services-ads:24.7.0")
    implementation("androidx.lifecycle:lifecycle-process:2.8.4")
    implementation("com.android.billingclient:billing:7.0.0")
    implementation("com.android.billingclient:billing-ktx:7.0.0")

    // implementation("com.google.android.gms:play-services-ads-api:24.7.0") // Usually included in play-services-ads

    // Firebase for Remote Config
    // Temporarily using explicit versions instead of BOM due to missing google-services.json
    implementation("com.google.firebase:firebase-config-ktx:22.0.0")
    implementation("com.google.firebase:firebase-analytics-ktx:22.1.0")
    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // JSON parsing (optional, for better JSON handling)
    implementation("com.google.code.gson:gson:2.11.0")
}