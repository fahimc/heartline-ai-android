import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.compose")
}

val bundledQwenFileName = "qwen3_0_6b_mixed_int4.litertlm"
val bundledQwenAssetDir = layout.projectDirectory.dir("src/main/assets/models")
val bundledQwenExpectedBytes = 497_664_000L
val bundledQwenExpectedSha256 = "b1baab462f6be49d70eada79d715c2c52cd9ece0cad00bddf6a2c097d23498e9"
val bundledQwenUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/$bundledQwenFileName"

val downloadBundledQwen by tasks.registering {
    group = "heartline"
    description = "Downloads the Qwen3 LiteRT asset into the APK assets directory."
    outputs.file(bundledQwenAssetDir.file(bundledQwenFileName))
    outputs.upToDateWhen {
        bundledQwenAssetDir.file(bundledQwenFileName).asFile.length() == bundledQwenExpectedBytes
    }

    doLast {
        val assetDir = bundledQwenAssetDir.asFile.apply { mkdirs() }
        assetDir.listFiles { file ->
            file.extension in setOf("litertlm", "llmpart", "download") && file.name != bundledQwenFileName
        }?.forEach { it.delete() }

        val output = assetDir.resolve(bundledQwenFileName)
        if (output.exists() && output.length() == bundledQwenExpectedBytes) {
            logger.lifecycle("Bundled Qwen3 asset already present at ${output.absolutePath}")
            return@doLast
        }

        val partial = assetDir.resolve("$bundledQwenFileName.download")
        output.delete()
        partial.delete()

        logger.lifecycle("Downloading bundled Qwen3 app asset (${bundledQwenExpectedBytes / 1_000_000} MB): $bundledQwenUrl")
        var totalBytes = 0L
        try {
            URI(bundledQwenUrl).toURL().openStream().buffered().use { input ->
                partial.outputStream().buffered().use { outputStream ->
                    val buffer = ByteArray(8 * 1024 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        outputStream.write(buffer, 0, read)
                        totalBytes += read
                    }
                }
            }
        } catch (error: Throwable) {
            partial.delete()
            throw error
        }

        if (totalBytes != bundledQwenExpectedBytes || partial.length() != bundledQwenExpectedBytes) {
            val actualBytes = partial.length()
            partial.delete()
            throw GradleException("Downloaded Qwen3 asset was $actualBytes bytes; expected $bundledQwenExpectedBytes bytes.")
        }
        val actualSha256 = MessageDigest.getInstance("SHA-256").let { digest ->
            partial.inputStream().buffered().use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString(separator = "") { "%02x".format(it) }
        }
        if (!actualSha256.equals(bundledQwenExpectedSha256, ignoreCase = true)) {
            partial.delete()
            throw GradleException("Downloaded Qwen3 asset failed verification.")
        }
        check(partial.renameTo(output)) { "Could not save bundled Qwen3 asset to ${output.absolutePath}" }
    }
}

android {
    namespace = "com.heartline.ai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.heartline.ai"
        minSdk = 26
        targetSdk = 34
        versionCode = 18
        versionName = "0.1.17"

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
    dependsOn(downloadBundledQwen)
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
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
