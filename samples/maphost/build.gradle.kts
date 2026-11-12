// Sample: how a launcher embeds RoadCast's live dashboard map. See docs/LAUNCHER_INTEGRATION.md.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.roadcast.maphost"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.roadcast.maphost"
        minSdk = 30 // SurfaceView.getHostToken and setChildSurfacePackage
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
