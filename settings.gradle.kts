val githubProps = java.util.Properties().apply {
    file("github.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

fun credential(key: String, env: String): String =
    githubProps.getProperty(key) ?: providers.gradleProperty(key).orNull ?: System.getenv(env) ?: ""

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        for (repo in listOf("sesl-androidx", "sesl-material-components-android", "oneui-design")) {
            maven {
                url = uri("https://maven.pkg.github.com/tribalfs/$repo")
                credentials {
                    username = credential("ghUsername", "GH_USERNAME")
                    password = credential("ghAccessToken", "GH_ACCESS_TOKEN")
                }
            }
        }
    }
}

rootProject.name = "Exiless"
include(":app")
