plugins {
    java
}

allprojects {
    group = "ru.vikulinva.orderservice"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showExceptions = true
            showStackTraces = true
            showCauses = true
        }
        // Передаём Docker-сокет в форкнутый JVM (Testcontainers на macOS с
        // Docker Desktop часто не находит сокет автоматически). Можно
        // переопределить через -PdockerHost=... в gradle.properties.
        val explicitHost = (findProperty("dockerHost") as String?) ?: System.getenv("DOCKER_HOST")
        val resolvedHost = explicitHost ?: listOf(
            "${System.getProperty("user.home")}/.docker/run/docker.sock",
            "/var/run/docker.sock"
        ).firstOrNull { file(it).exists() }?.let { "unix://$it" }
        resolvedHost?.let { host ->
            environment("DOCKER_HOST", host)
            systemProperty("docker.host", host)
        }
    }

    repositories {
        mavenCentral()
        mavenLocal()
    }
}
