// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
//plug-in do Gradle para os Serviços do Google.
    id("com.google.gms.google-services") version "4.4.4" apply false
}