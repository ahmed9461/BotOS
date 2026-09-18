plugins { alias(libs.plugins.android.library) }
android {
    namespace = "com.ahmed9461.botos.tdlib"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    sourceSets["main"].jniLibs.srcDirs(
        rootProject.file("native/output/arm64-v8a/jniLibs"),
        rootProject.file("native/output/x86_64/jniLibs"),
    )
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    lint { abortOnError = true }
}
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":core:telegram"))
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
