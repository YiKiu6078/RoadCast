plugins { alias(libs.plugins.android.application) }
android {
    namespace = "com.roadcast.companion"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "com.roadcast.companion"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.17")
}
tasks.withType<Test>().configureEach {
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
}
androidComponents.onVariants { variant ->
    variant.sources.res?.addStaticSourceDirectory("../companion-ui/res")
}
