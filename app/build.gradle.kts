import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.compose")
}

val bundledLlmFileName = "gemma-4-E2B-it.litertlm"
val bundledLlmPartPrefix = "gemma-4-E2B-it"
val bundledLlmAssetDir = layout.projectDirectory.dir("src/main/assets/models")
val bundledLlmExpectedBytes = 2_588_147_712L
val bundledLlmPartBytes = 512L * 1024L * 1024L
val bundledLlmUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/$bundledLlmFileName"

val downloadBundledLlm by tasks.registering {
    group = "heartline"
    description = "Downloads the Apache-2.0 Gemma 4 E2B LiteRT-LM bundle into Android assets."
    outputs.dir(bundledLlmAssetDir)
    outputs.upToDateWhen {
        val parts = bundledLlmAssetDir.asFile
            .listFiles { file -> file.name.startsWith("$bundledLlmPartPrefix-") && file.name.endsWith(".llmpart") }
            ?.toList()
            .orEmpty()
        parts.isNotEmpty() && parts.sumOf { it.length() } == bundledLlmExpectedBytes
    }

    doLast {
        val assetDir = bundledLlmAssetDir.asFile
        val existingParts = assetDir
            .listFiles { file -> file.name.startsWith("$bundledLlmPartPrefix-") && file.name.endsWith(".llmpart") }
            ?.toList()
            .orEmpty()
        if (existingParts.isNotEmpty() && existingParts.sumOf { it.length() } == bundledLlmExpectedBytes) {
            logger.lifecycle("Bundled Gemma 4 LLM parts already present in ${assetDir.absolutePath}")
            return@doLast
        }

        assetDir.mkdirs()
        assetDir.listFiles { file ->
            file.name == bundledLlmFileName ||
                file.name.startsWith("$bundledLlmPartPrefix-") && file.name.endsWith(".llmpart") ||
                file.name.startsWith("OLMo-2-1B-Instruct")
        }?.forEach { it.delete() }

        fun partFile(index: Int) = assetDir.resolve("$bundledLlmPartPrefix-${index.toString().padStart(3, '0')}.llmpart")
        fun deleteParts() {
            assetDir.listFiles { file -> file.name.startsWith("$bundledLlmPartPrefix-") && file.name.endsWith(".llmpart") }
                ?.forEach { it.delete() }
        }

        logger.lifecycle("Downloading bundled Gemma 4 LLM (${bundledLlmExpectedBytes / 1_000_000} MB): $bundledLlmUrl")
        var totalBytes = 0L
        var partIndex = 0
        var partBytes = 0L
        var output = partFile(partIndex).outputStream().buffered()
        try {
            URI(bundledLlmUrl).toURL().openStream().buffered().use { input ->
                val buffer = ByteArray(8 * 1024 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    var offset = 0
                    while (offset < read) {
                        if (partBytes == bundledLlmPartBytes) {
                            output.close()
                            partIndex += 1
                            partBytes = 0L
                            output = partFile(partIndex).outputStream().buffered()
                        }
                        val write = minOf(read - offset, (bundledLlmPartBytes - partBytes).toInt())
                        output.write(buffer, offset, write)
                        offset += write
                        partBytes += write
                        totalBytes += write
                    }
                }
            }
        } catch (error: Throwable) {
            output.close()
            deleteParts()
            throw error
        } finally {
            output.close()
        }

        if (totalBytes != bundledLlmExpectedBytes) {
            deleteParts()
            throw GradleException("Downloaded LLM size was $totalBytes bytes; expected $bundledLlmExpectedBytes bytes.")
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
        versionCode = 5
        versionName = "0.1.4"

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
        noCompress += "llmpart"
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
