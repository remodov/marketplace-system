import org.jooq.meta.jaxb.ForcedType
import org.jooq.meta.jaxb.MatcherRule
import org.jooq.meta.jaxb.MatcherTransformType
import org.jooq.meta.jaxb.Matchers
import org.jooq.meta.jaxb.MatchersTableType

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.jooq.codegen)
    alias(libs.plugins.liquibase)
}

group = "ru.vikulinva"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get().toInt()))
    }
}

dependencies {
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.jooq)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.kafka)

    runtimeOnly(libs.postgresql)
    implementation(libs.liquibase.core)
    implementation(libs.hikaricp)

    implementation(libs.resilience4j.spring.boot3)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.jsr310)
    implementation(libs.micrometer.prometheus)

    // jOOQ codegen — JDBC-драйвер + jOOQ артефакты на этап генерации.
    jooqGenerator(libs.postgresql)
    jooqGenerator("org.jooq:jooq-meta:${libs.versions.jooq.get()}")
    jooqGenerator("org.jooq:jooq-codegen:${libs.versions.jooq.get()}")

    // Liquibase Gradle plugin runtime — для команды `./gradlew update` (локально).
    liquibaseRuntime("org.liquibase:liquibase-core:${libs.versions.liquibase.get()}")
    liquibaseRuntime(libs.postgresql)
    liquibaseRuntime("info.picocli:picocli:4.7.7")
    liquibaseRuntime("ch.qos.logback:logback-classic:1.5.18")
    liquibaseRuntime("org.yaml:snakeyaml:2.4")

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.spring.kafka.test)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.wiremock)
    testImplementation(libs.wiremock.jetty12)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.mockito.junit.jupiter)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all", "-Xlint:-processing"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    forkEvery = 1
    maxParallelForks = 1
}

springBoot {
    mainClass.set("ru.vikulinva.notificationservice.NotificationServiceApplication")
}

// Параметры подключения к локальному Postgres (docker-compose, порт 5433).
val dbUrl: String = (findProperty("notificationservice.db.url") as String?) ?: "jdbc:postgresql://localhost:5433/notifications"
val dbUser: String = (findProperty("notificationservice.db.user") as String?) ?: "notifications"
val dbPassword: String = (findProperty("notificationservice.db.password") as String?) ?: "notifications"

liquibase {
    activities.register("main") {
        arguments = mapOf(
            "logLevel" to "info",
            "changelogFile" to "db/changelog-master.yaml",
            "searchPath" to "${rootDir}/migrations",
            "url" to dbUrl,
            "username" to dbUser,
            "password" to dbPassword,
            "driver" to "org.postgresql.Driver"
        )
    }
    runList = "main"
}

jooq {
    version.set(libs.versions.jooq.get())
    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(false)
            jooqConfiguration.apply {
                logging = org.jooq.meta.jaxb.Logging.WARN
                jdbc.apply {
                    driver = "org.postgresql.Driver"
                    url = dbUrl
                    user = dbUser
                    password = dbPassword
                }
                generator.apply {
                    name = "org.jooq.codegen.JavaGenerator"
                    database.apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = "public"
                        excludes = "databasechangelog|databasechangeloglock"
                        forcedTypes.add(
                            ForcedType().apply {
                                name = "OffsetDateTime"
                                types = "TIMESTAMP"
                            }
                        )
                    }
                    target.apply {
                        packageName = "ru.vikulinva.notificationservice.generated"
                        directory = "build/generated/jooq"
                    }
                    generate.apply {
                        isPojos = true
                        isRecords = true
                        isFluentSetters = false
                        isImmutablePojos = false
                        isDeprecated = false
                    }
                    strategy.apply {
                        matchers = Matchers().withTables(
                            MatchersTableType().withPojoClass(
                                MatcherRule()
                                    .withTransform(MatcherTransformType.PASCAL)
                                    .withExpression("$0_Pojo")
                            )
                        )
                    }
                }
            }
        }
    }
}

sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("generated/jooq"))
        }
        resources {
            // Liquibase changelog лежит в migrations/ на уровне репо.
            srcDir(rootProject.file("migrations"))
        }
    }
}

// Удобство для local dev: применяет Liquibase + регенерирует jOOQ.
tasks.register("regenerate") {
    group = "build"
    description = "Run Liquibase update + jOOQ codegen against local Postgres"
    dependsOn("update", "generateJooq")
    tasks.findByName("generateJooq")?.mustRunAfter("update")
}
