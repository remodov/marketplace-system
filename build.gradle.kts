plugins {
    java
    checkstyle
}

allprojects {
    group = "ru.vikulinva.customer"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "checkstyle")

    repositories {
        mavenCentral()
        mavenLocal()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    extensions.configure<CheckstyleExtension> {
        toolVersion = "10.20.2"
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        configProperties = mapOf("baseDir" to rootProject.projectDir.absolutePath)
        maxWarnings = 0
        isIgnoreFailures = false
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-parameters"))
    }
}
