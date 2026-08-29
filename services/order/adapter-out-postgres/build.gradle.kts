import org.jooq.meta.jaxb.ForcedType
import org.jooq.meta.jaxb.MatcherRule
import org.jooq.meta.jaxb.MatcherTransformType
import org.jooq.meta.jaxb.Matchers
import org.jooq.meta.jaxb.MatchersTableType

plugins {
    `java-library`
    id("nu.studer.jooq") version "10.2"
    id("org.liquibase.gradle") version "2.2.2"
}

dependencies {
    api(project(":core"))

    implementation(libs.spring.boot.starter.jooq)
    runtimeOnly(libs.postgresql)
    implementation(libs.hikaricp)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")

    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)

    // Liquibase Spring Boot integration (для запуска при старте приложения).
    implementation("org.liquibase:liquibase-core:4.29.2")

    // jOOQ codegen — JDBC-драйвер + jOOQ артефакты на этап генерации.
    jooqGenerator(libs.postgresql)
    jooqGenerator("org.jooq:jooq-meta:${libs.versions.jooq.get()}")
    jooqGenerator("org.jooq:jooq-codegen:${libs.versions.jooq.get()}")

    // Liquibase Gradle plugin runtime — для команды `./gradlew :adapter-out-postgres:update`.
    liquibaseRuntime("org.liquibase:liquibase-core:4.29.2")
    liquibaseRuntime(libs.postgresql)
    liquibaseRuntime("info.picocli:picocli:4.7.7")
    liquibaseRuntime("ch.qos.logback:logback-classic:1.5.18")
    liquibaseRuntime("org.yaml:snakeyaml:2.4")

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.testcontainers.junit.jupiter)
}

// Параметры подключения к локальному Postgres (docker-compose).
// На CI переопределяются через -P, в IDE — через gradle.properties.
val dbUrl: String = (findProperty("orderservice.db.url") as String?) ?: "jdbc:postgresql://localhost:5432/orders"
val dbUser: String = (findProperty("orderservice.db.user") as String?) ?: "orders"
val dbPassword: String = (findProperty("orderservice.db.password") as String?) ?: "orders"

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
                        packageName = "ru.vikulinva.orderservice.adapter.out.postgres.generated"
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
        // Liquibase changelog лежит в migrations/ на уровне репо.
        // Подкладываем в classpath модуля, чтобы Spring Boot Liquibase нашёл его при старте.
        resources {
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
