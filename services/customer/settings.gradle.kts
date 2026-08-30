rootProject.name = "customer"

include("core")
include("persistence")
include("user-in-adapter")
include("kafka-out-adapter")
include("bootstrap")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}
