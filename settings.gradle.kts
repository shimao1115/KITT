pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "KITT"
include(":app")
// D1 release-format tooling. Pure JVM, no Android dependency, never packaged into the APK.
include(":packtool")
