plugins {
    alias(libs.plugins.android.library)
}
android {
    namespace = "com.ahmed9461.botos.core.data"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:telegram"))
    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.android)
}
