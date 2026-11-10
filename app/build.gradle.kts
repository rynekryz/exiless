import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.BuiltArtifactsLoader
import com.android.build.api.variant.FilterConfiguration
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val appVersion = "0.0.2-alpha"

abstract class RenameApksTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkFolder: DirectoryProperty

    @get:Internal
    abstract val loader: Property<BuiltArtifactsLoader>

    @get:Input
    abstract val version: Property<String>

    @get:Input
    abstract val buildType: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val built = loader.get().load(apkFolder.get()) ?: error("No APKs found")
        val out = outputDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        for (apk in built.elements) {
            val abi = apk.filters.firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }?.identifier
            val label = when (abi) {
                null -> "universal"
                "arm64-v8a" -> "arm64"
                "armeabi-v7a" -> "armv7"
                else -> abi
            }
            File(apk.outputFile).copyTo(File(out, "exiless-${version.get()}-$label-${buildType.get()}.apk"), overwrite = true)
        }
    }
}

android {
    namespace = "com.rynekryz.exiless"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.rynekryz.exiless"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = appVersion
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // Local signing
    val keystoreProps = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    val hasKeystore = keystoreProps.containsKey("storeFile")
    if (hasKeystore) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

androidComponents {
    onVariants { variant ->
        val rename = tasks.register<RenameApksTask>("rename${variant.name.replaceFirstChar { it.uppercase() }}Apks") {
            apkFolder.set(variant.artifacts.get(SingleArtifact.APK))
            loader.set(variant.artifacts.getBuiltArtifactsLoader())
            version.set(appVersion)
            buildType.set(variant.buildType ?: variant.name)
            outputDir.set(layout.buildDirectory.dir("outputs/apk-named/${variant.name}"))
        }
        tasks.matching { it.name == "assemble${variant.name.replaceFirstChar { it.uppercase() }}" }
            .configureEach { finalizedBy(rename) }
    }
}

// sesl replaces
configurations.all {
    exclude("androidx.core", "core")
    exclude("androidx.core", "core-ktx")
    exclude("androidx.fragment", "fragment")
    exclude("androidx.fragment", "fragment-ktx")
    exclude("androidx.customview", "customview")
    exclude("androidx.coordinatorlayout", "coordinatorlayout")
    exclude("androidx.drawerlayout", "drawerlayout")
    exclude("androidx.viewpager2", "viewpager2")
    exclude("androidx.viewpager", "viewpager")
    exclude("androidx.appcompat", "appcompat")
    exclude("androidx.preference", "preference")
    exclude("androidx.recyclerview", "recyclerview")
    exclude("androidx.slidingpanelayout", "slidingpanelayout")
    exclude("androidx.swiperefreshlayout", "swiperefreshlayout")
    exclude("com.google.android.material", "material")
    // Proprietary
    exclude("com.google.android.gms")
    exclude("com.google.mlkit")
}

dependencies {
    implementation(libs.oneui.design)
    implementation(libs.oneui.icons)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
}
