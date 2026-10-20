plugins { id("com.android.application"); kotlin("android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace = "com.poolaim.app"
 compileSdk = 35; buildToolsVersion = "35.0.0"
 defaultConfig {
  applicationId = "com.poolaim.app"; minSdk = 26; targetSdk = 35
  versionCode = 2; versionName = "0.2.0"
  testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildFeatures { compose = true }
}
dependencies {
 implementation("androidx.core:core-ktx:1.15.0")
 implementation(project(":physics")); implementation(project(":calibration"))
 implementation(project(":capture")); implementation(project(":recognition")); implementation(project(":overlay"))
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.ui:ui-tooling-preview")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
 debugImplementation("androidx.compose.ui:ui-tooling")
 androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
 androidTestImplementation("androidx.compose.ui:ui-test-junit4")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
 androidTestImplementation("androidx.test:rules:1.6.1")
 androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
 debugImplementation("androidx.compose.ui:ui-test-manifest")
}
