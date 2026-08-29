rootProject.name = "order-service"

include(
    "core",
    "adapter-in-rest",
    "adapter-in-kafka",
    "adapter-out-postgres",
    "adapter-out-payment",
    "adapter-out-catalog",
    "adapter-out-kafka",
    "bootstrap",
    "test-utils"
)

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
        // remodov libs (usecase-pattern, ddd-building-blocks, hexagonal-architecture) —
        // публикуются в GitHub Packages. Для локальной сборки достаточно mavenLocal()
        // если предварительно `./gradlew publishToMavenLocal` в их репозиториях.
        maven {
            name = "remodov-usecase-pattern"
            url = uri("https://maven.pkg.github.com/remodov/usecase-pattern")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: providers.gradleProperty("gpr.user").orNull
                password = System.getenv("GITHUB_TOKEN") ?: providers.gradleProperty("gpr.key").orNull
            }
        }
        maven {
            name = "remodov-ddd-building-blocks"
            url = uri("https://maven.pkg.github.com/remodov/ddd-building-blocks")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: providers.gradleProperty("gpr.user").orNull
                password = System.getenv("GITHUB_TOKEN") ?: providers.gradleProperty("gpr.key").orNull
            }
        }
        maven {
            name = "remodov-hexagonal-architecture"
            url = uri("https://maven.pkg.github.com/remodov/hexagonal-architecture")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: providers.gradleProperty("gpr.user").orNull
                password = System.getenv("GITHUB_TOKEN") ?: providers.gradleProperty("gpr.key").orNull
            }
        }
    }
}
