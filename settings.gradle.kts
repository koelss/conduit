@file:Suppress("UnstableApiUsage")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        // Hosts gg.gemstone:component and other Velocity-CTD artifacts not on Maven Central.
        maven("https://repo.velocityctd.com/releases") {
            name = "velocityctdReleases"
        }
    }
}

pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "conduit"

sequenceOf(
    "api",
    "native",
    "proxy",
    "bootstrap",
).forEach {
    val project = ":velocity-$it"
    include(project)
    project(project).projectDir = file(it)
}

// Permission integration modules (upstream reorganized the former luckperms-integration
// module into a permission-integration SPI plus per-provider adapters).
val permissionIntegrationSpi = ":velocity-permission-integration-spi"
include(permissionIntegrationSpi)
project(permissionIntegrationSpi).projectDir = file("permission-integration/spi")

val permissionIntegrationLuckperms = ":velocity-permission-integration-luckperms"
include(permissionIntegrationLuckperms)
project(permissionIntegrationLuckperms).projectDir = file("permission-integration/luckperms")

val deprecatedConfigurateModule = ":deprecated-configurate3"
include(deprecatedConfigurateModule)
project(deprecatedConfigurateModule).projectDir = file("proxy/deprecated/configurate3")
