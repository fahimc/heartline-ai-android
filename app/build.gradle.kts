import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.compose")
}

val bundledLlmFileName = "OLMo-2-1B-Instruct_q4_block32_ekv4096.litertlm"
val bundledLlmAssetPath = "src/main/assets/models/$bundledLlmFileName"
val bundledLlmExpectedBytes = 931_241_056L
val bundledLlmUrl = "https://huggingface.co/litert-community/OLMo-2-1B-Instruct/resolve/main/$bundledLlmFileName"
val bundledLlmFile = layout.projectDirectory.file(bundledLlmAssetPath)

val downloadBundledLlm by tasks.registering {
    group = "heartline"
    description = "Downloads the Apache-2.0 OLMo LiteRT-LM bundle into Android assets."
    outputs.file(bundledLlmFile)

    doLast {
        val destination = bundledLlmFile.asFile
        if (destination.exists() && destination.length() == bundledLlmExpectedBytes) {
            logger.lifecycle("Bundled LLM already present: ${destination.absolutePath}")
            return@doLast
        }

        destination.parentFile.mkdirs()
        val partial = destination.resolveSibling("${destination.name}.download")
        if (partial.exists()) partial.delete()

        logger.lifecycle("Downloading bundled LLM (${bundledLlmExpectedBytes / 1_000_000} MB): $bundledLlmUrl")
        URI(bundledLlmUrl).toURL().openStream().use { input ->
            partial.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val downloadedBytes = partial.length()
        if (downloadedBytes != bundledLlmExpectedBytes) {
            partial.delete()
            throw GradleException("Downloaded LLM size was $downloadedBytes bytes; expected $bundledLlmExpectedBytes bytes.")
        }

        if (destination.exists()) destination.delete()
        if (!partial.renameTo(destination)) {
            throw GradleException("Could not move downloaded LLM into ${destination.absolutePath}.")
        }
    }
}

android {
    namespace = "com.heartline.ai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.heartline.ai"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        noCompress += "litertlm"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.named("preBuild") {
    dependsOn(downloadBundledLlm)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    kapt("androidx.room:room-compiler:2.8.4")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.14.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
