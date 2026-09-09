pluginManagement {
    val viaductVersion: String by settings

    repositories {
        mavenLocal()
        if (viaductVersion.endsWith("-SNAPSHOT")) {
            maven("https://central.sonatype.com/repository/maven-snapshots/")
        }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    val viaductVersion: String by settings

    repositories {
        mavenLocal()
        if (viaductVersion.endsWith("-SNAPSHOT")) {
            maven("https://central.sonatype.com/repository/maven-snapshots/")
        }
        mavenCentral()
    }
}

rootProject.name = "viaduct-pg-persistence"

include(
    ":runtime",
    ":plugin",
)
