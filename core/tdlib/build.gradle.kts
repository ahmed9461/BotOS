plugins { alias(libs.plugins.android.library) }
android {
    namespace = "com.ahmed9461.botos.tdlib"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    lint { abortOnError = true }
}
val nativeDirectories = listOf("arm64-v8a", "x86_64").map { abi ->
    abi to rootProject.file("native/output/$abi/jniLibs")
}
// Use the public variant API; the legacy AndroidLibrarySourceSet cast is invalid in AGP 9.
androidComponents {
    onVariants(selector().all()) { variant ->
        nativeDirectories.filter { it.second.isDirectory }.forEach { (_, directory) ->
            variant.sources.jniLibs?.addStaticSourceDirectory(directory.absolutePath)
        }
    }
}
val verifyNativeInputs = tasks.register("verifyNativeInputs") {
    doLast {
        nativeDirectories.forEach { (abi, directory) ->
            check(directory.resolve("$abi/libtdjsonjava.so").isFile) {
                "Missing pinned TDLib runtime for $abi. Run scripts/build_tdlib.sh for both ABIs first."
            }
        }
    }
}
tasks.named("preBuild") { dependsOn(verifyNativeInputs) }
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":core:telegram"))
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
