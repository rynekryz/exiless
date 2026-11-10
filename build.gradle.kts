// AGP's built-in Kotlin is too old for the Kotlin 2.4 metadata used by oneui-design
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
}
