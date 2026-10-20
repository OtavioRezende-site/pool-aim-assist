pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "Pool Aim Assist"
include(":app", ":physics", ":calibration", ":capture", ":recognition", ":overlay")

include(":vision-api")
