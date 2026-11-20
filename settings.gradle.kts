pluginManagement {
    repositories {
        google()
        // Google-hosted Maven Central mirror prevents transient CDN 403s on CI.
        // Keep the official endpoints below for artifacts absent from the mirror.
        maven {
            url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven {
            url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
        }
        mavenCentral()
    }
}
rootProject.name = "tkb"
include(":app")
