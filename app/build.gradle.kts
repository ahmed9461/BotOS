import com.android.build.api.variant.BuildConfigField
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}
// Select one complete source. Never mix an environment ID with a local hash.
val localTelegramProperties = Properties().apply {
    val source = rootProject.file("local.properties")
    if (source.isFile) source.inputStream().use { load(it) }
}
val environmentApiId = providers.environmentVariable("BOTOS_TELEGRAM_API_ID").orNull
val environmentApiHash = providers.environmentVariable("BOTOS_TELEGRAM_API_HASH").orNull
val environmentSelected = environmentApiId != null || environmentApiHash != null
val telegramApiIdText = if (environmentSelected) environmentApiId.orEmpty()
    else localTelegramProperties.getProperty("BOTOS_TELEGRAM_API_ID", "")
val telegramApiHash = if (environmentSelected) environmentApiHash.orEmpty()
    else localTelegramProperties.getProperty("BOTOS_TELEGRAM_API_HASH", "")
val telegramConfigured = telegramApiIdText.isNotEmpty() || telegramApiHash.isNotEmpty()
val telegramApiId = telegramApiIdText.toIntOrNull()
if (telegramConfigured && (!Regex("[1-9][0-9]{0,9}").matches(telegramApiIdText) ||
        telegramApiId == null || telegramApiId <= 0 ||
        !Regex("[0-9a-fA-F]{32}").matches(telegramApiHash))) {
    throw GradleException("Invalid Telegram application configuration; provide a complete valid pair outside Git.")
}
if (providers.environmentVariable("BOTOS_REQUIRE_TELEGRAM_CONFIG").orNull == "true" && !telegramConfigured) {
    throw GradleException("Telegram application configuration is required for this build.")
}

android {
    namespace = "com.ahmed9461.botos"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.ahmed9461.botos"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["launcherLabel"] = "@string/app_name"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    // Install beside the earlier differently-signed previews; never erase their local library.
    buildTypes {
        create("ownerPreview") {
            initWith(getByName("release"))
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug") // CI smoke only; owner signs final APK offline.
            matchingFallbacks += listOf("debug") // Exactly the previously-tested native/library variants.
            manifestPlaceholders["launcherLabel"] = "BotOS Preview"
        }
    }
    testBuildType = if (providers.gradleProperty("botos.testOwnerPreview").orNull == "true") "ownerPreview" else "debug"
    lint { abortOnError = true }
}
// Public AGP variant API; generated values never belong in a source artifact or build cache.
androidComponents.onVariants { variant ->
    val fields = checkNotNull(variant.buildConfigFields)
    fields.put("TELEGRAM_CONFIGURED", BuildConfigField("boolean", telegramConfigured, null))
    fields.put("TELEGRAM_API_ID", BuildConfigField("int", telegramApiId ?: 0, null))
    fields.put("TELEGRAM_API_HASH", BuildConfigField("String", "\"$telegramApiHash\"", null))
}
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:telegram"))
    implementation(project(":core:tdlib"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.savedstate)
    implementation(libs.navigation3.ui)
    implementation(libs.navigation3.runtime)
    implementation(libs.coroutines.android)
    debugImplementation(libs.compose.tooling)
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
