import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.compose")
}

val bundledSmolLmFileName = "SmolLM2_135M_Instruct.litertlm"
val bundledSmolLmAssetDir = layout.projectDirectory.dir("src/main/assets/models")
val bundledSmolLmExpectedBytes = 142_819_328L
val bundledSmolLmExpectedSha256 = "ccdc5c85735743f081b7d44ca309cab569f76c0f2f0e8e163449a63721969c37"
val bundledSmolLmUrl = "https://huggingface.co/litert-community/SmolLM2-135M-Instruct/resolve/main/$bundledSmolLmFileName"

val downloadBundledSmolLm by tasks.registering {
    group = "heartline"
    description = "Downloads the SmolLM2 LiteRT asset into the APK assets directory."
    outputs.file(bundledSmolLmAssetDir.file(bundledSmolLmFileName))
    outputs.upToDateWhen {
        bundledSmolLmAssetDir.file(bundledSmolLmFileName).asFile.length() == bundledSmolLmExpectedBytes
    }

    doLast {
        val assetDir = bundledSmolLmAssetDir.asFile.apply { mkdirs() }
        assetDir.listFiles { file ->
            file.extension in setOf("litertlm", "llmpart", "download") && file.name != bundledSmolLmFileName
        }?.forEach { it.delete() }

        val output = assetDir.resolve(bundledSmolLmFileName)
        if (output.exists() && output.length() == bundledSmolLmExpectedBytes) {
            logger.lifecycle("Bundled SmolLM2 asset already present at ${output.absolutePath}")
            return@doLast
        }

        val partial = assetDir.resolve("$bundledSmolLmFileName.download")
        output.delete()
        partial.delete()

        logger.lifecycle("Downloading bundled SmolLM2 app asset (${bundledSmolLmExpectedBytes / 1_000_000} MB): $bundledSmolLmUrl")
        var totalBytes = 0L
        try {
            URI(bundledSmolLmUrl).toURL().openStream().buffered().use { input ->
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

        if (totalBytes != bundledSmolLmExpectedBytes || partial.length() != bundledSmolLmExpectedBytes) {
            val actualBytes = partial.length()
            partial.delete()
            throw GradleException("Downloaded SmolLM2 asset was $actualBytes bytes; expected $bundledSmolLmExpectedBytes bytes.")
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
        if (!actualSha256.equals(bundledSmolLmExpectedSha256, ignoreCase = true)) {
            partial.delete()
            throw GradleException("Downloaded SmolLM2 asset failed verification.")
        }
        check(partial.renameTo(output)) { "Could not save bundled SmolLM2 asset to ${output.absolutePath}" }
    }
}

android {
    namespace = "com.heartline.ai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.heartline.ai"
        minSdk = 26
        targetSdk = 34
        versionCode = 15
        versionName = "0.1.14"

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
    dependsOn(downloadBundledSmolLm)
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
