import java.util.Properties
import java.util.Base64
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.google.ksp)
}

val supabaseProperties = Properties().apply {
    // Publishable client configuration is safe to distribute with the app.
    rootProject.file("supabase.public.properties").inputStream().use { load(it) }
    val config = rootProject.file("supabase.properties")
    if (config.isFile) {
        val overrides = Properties().apply { config.inputStream().use { load(it) } }
        overrides.forEach { name, value ->
            if (value.toString().isNotBlank()) setProperty(name.toString(), value.toString().trim())
        }
    }
}
val configuredClientKey = supabaseProperties.getProperty("SUPABASE_PUBLISHABLE_KEY", "").trim()
val legacyAnonymousKey = runCatching {
    val payload = configuredClientKey.split('.').getOrNull(1) ?: return@runCatching false
    val decoded = String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
    val claims = groovy.json.JsonSlurper().parseText(decoded) as? Map<*, *>
    claims?.get("role") == "anon"
}.getOrDefault(false)
require(configuredClientKey.startsWith("sb_publishable_") || legacyAnonymousKey) {
    "Only a Supabase public publishable or anon client key may be included in the app."
}

fun javaString(value: String): String = "\"" + value
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\r", "\\r")
    .replace("\n", "\\n")
    .replace("\t", "\\t") + "\""

android {
    namespace = "com.example.dinefinder"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.dinefinder"
        minSdk = 28
        targetSdk = 35
        versionCode = 12
        versionName = "0.5.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "SUPABASE_URL",
            javaString(supabaseProperties.getProperty("SUPABASE_URL", "").trim()),
        )
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            javaString(supabaseProperties.getProperty("SUPABASE_PUBLISHABLE_KEY", "").trim()),
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.ar:core:1.48.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.robolectric)
    testImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.json:json:20250517")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.google.material)
    implementation(libs.google.maps.utils)
    implementation(libs.play.services.location)
}
