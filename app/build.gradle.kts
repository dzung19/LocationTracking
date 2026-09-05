import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.services)
}
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
  localProperties.load(localPropertiesFile.inputStream())
}
android {
  namespace = "com.dzung.runner.locationtracker"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.dzung.runner.locationtracker"
    minSdk = 28
    targetSdk = 37
    versionCode = 3
    versionName = "1.1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    manifestPlaceholders["MAPS_API_KEY"] = "dummy_key"
  }

  signingConfigs {
    val releaseKeystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
    val releaseKeystoreFile = file(releaseKeystorePath)
    if (releaseKeystoreFile.exists()) {
      create("release") {
        storeFile = releaseKeystoreFile
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
    val debugKeystoreFile = file("${rootDir}/debug.keystore")
    if (debugKeystoreFile.exists()) {
      create("debugConfig") {
        storeFile = debugKeystoreFile
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      buildConfigField("String", "APP_OPEN_AD_ID", "\"${localProperties.getProperty("APP_OPEN_AD_ID")}\"")
      buildConfigField("String", "BANNER_AD_ID", "\"${localProperties.getProperty("BANNER_AD_ID")}\"")
      buildConfigField("String", "INTERSTITIAL_AD_ID", "\"${localProperties.getProperty("INTERSTITIAL_AD_ID")}\"")
      buildConfigField("String", "REMOVE_ADS_SKU", "\"remove_ads_sku\"")
      buildConfigField("Boolean", "ADS_DISABLED", "false")
      manifestPlaceholders["caAppPubId"] = "ca-app-pub-9439921169677011~6105322422"
      signingConfigs.findByName("release")?.let {
        signingConfig = it
      }
    }
    debug {
      buildConfigField("String", "APP_OPEN_AD_ID", "\"ca-app-pub-3940256099942544/9257395921\"")
      buildConfigField("String", "BANNER_AD_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
      buildConfigField("String", "INTERSTITIAL_AD_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
      buildConfigField("String", "REMOVE_ADS_SKU", "\"android.test.purchased\"")
      buildConfigField("Boolean", "ADS_DISABLED", "false")
      manifestPlaceholders["caAppPubId"] = "ca-app-pub-3940256099942544~3347511713"
      buildConfigField("Boolean", "ADS_DISABLED", "false")
      signingConfigs.findByName("debugConfig")?.let {
        signingConfig = it
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}



// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.play.services.location)
  implementation(libs.maps.compose)
  implementation(libs.play.services.maps)
  implementation(libs.retrofit)
  
  // Koin
  implementation(platform(libs.koin.bom))
  implementation(libs.koin.core)
  implementation(libs.koin.android)
  implementation(libs.koin.androidx.compose)
  
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  ksp(libs.androidx.room.compiler)
  ksp(libs.moshi.kotlin.codegen)

  implementation(libs.googleAds)
  implementation(project(":ads"))
}


