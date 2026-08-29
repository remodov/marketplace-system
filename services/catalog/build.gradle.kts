import org.jooq.meta.jaxb.Logging
import org.jooq.meta.jaxb.ForcedType
import org.jooq.meta.jaxb.Matchers
import org.jooq.meta.jaxb.MatchersTableType

plugins {
    java
    id("org.springframework.boot") version "3.4.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.liquibase.gradle") version "2.2.2"
    id("nu.studer.jooq") version "10.1"
    id("org.openapi.generator") version "7.10.0"
}

group = "ru.remodov"
version = "0.0.1-SNAPSHOT"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://maven.pkg.github.com/remodov/usecase-pattern") }
}

// BS-10: миграции лежат в /migrations на уровне репо, не в src/main/resources
sourceSets["main"].resources.srcDir(rootProject.file("migrations"))

val pgUrl = providers.gradleProperty("local.db.url")
    .orElse("jdbc:postgresql://localhost:5432/catalog")
val pgUser = providers.gradleProperty("local.db.user").orElse("catalog")
val pgPass = providers.gradleProperty("local.db.password").orElse("catalog")

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation("org.liquibase:liquibase-core")
    implementation("org.postgresql:postgresql")

    // usecase-pattern library (BS — UCP Tier B core)
    // Версия выставится при первом mvn-resolve; placeholder для bootstrap-скилла.
    implementation("ru.vikulinva:usecase-pattern:1.1.0")
    implementation("ru.vikulinva:usecase-pattern-starter:1.1.0")

    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.opentelemetry:opentelemetry-api:1.45.0")

    runtimeOnly("org.postgresql:postgresql")

    jooqGenerator("org.postgresql:postgresql")

    liquibaseRuntime("org.liquibase:liquibase-core:4.30.0")
    liquibaseRuntime("org.postgresql:postgresql:42.7.4")
    liquibaseRuntime("info.picocli:picocli:4.7.6")
    liquibaseRuntime("ch.qos.logback:logback-classic")

    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
    testCompileOnly("org.projectlombok:lombok:1.18.34")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.34")

    implementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    // Lombok+MapStruct interop: чтобы Lombok-сгенерированные ctor'ы были видны MapStruct'у.
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("com.github.tomakehurst:wiremock-jre8-standalone:3.0.1")
}

// BS-17/18/19: jOOQ codegen из applied-схемы локального Postgres
jooq {
    version.set("3.19.18")
    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(false)
            jooqConfiguration.apply {
                logging = Logging.WARN
                jdbc.apply {
                    driver = "org.postgresql.Driver"
                    url = pgUrl.get()
                    user = pgUser.get()
                    password = pgPass.get()
                }
                generator.apply {
                    name = "org.jooq.codegen.JavaGenerator"
                    strategy.apply {
                        matchers = Matchers().withTables(listOf(
                            MatchersTableType().apply {
                                pojoClass = org.jooq.meta.jaxb.MatcherRule()
                                    .withExpression("$0_Pojo")
                                    .withTransform(org.jooq.meta.jaxb.MatcherTransformType.PASCAL)
                            }
                        ))
                    }
                    database.apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = "public"
                        excludes = "databasechangelog|databasechangeloglock"
                        forcedTypes.addAll(listOf(
                            ForcedType().apply {
                                userType = "java.time.OffsetDateTime"
                                includeTypes = "TIMESTAMP\\ WITH\\ TIME\\ ZONE|TIMESTAMPTZ"
                            }
                        ))
                    }
                    target.apply {
                        packageName = "ru.remodov.catalog.generated"
                        directory = "build/generated/jooq/main"
                    }
                    generate.apply {
                        isPojos = true
                        isRecords = true
                        isJavaTimeTypes = true
                        isFluentSetters = true
                    }
                }
            }
        }
    }
}

liquibase {
    activities.register("main") {
        this.arguments = mapOf(
            "logLevel" to "info",
            "changelogFile" to "db/changelog-master.yaml",
            "searchPath" to rootProject.file("migrations").absolutePath,
            "url" to pgUrl.get(),
            "username" to pgUser.get(),
            "password" to pgPass.get(),
            "contexts" to "production"
        )
    }
    runList = "main"
}

// Удобный shortcut: накатить миграции и сгенерить jOOQ-классы (style guide §7)
tasks.register("regenerate") {
    group = "build"
    description = "liquibase update + jooq generate from applied schema"
    dependsOn("update", "generateJooq")
    tasks.findByName("generateJooq")?.mustRunAfter("update")
}

// Style-guide §12.2: REST-контракт идёт от OpenAPI. Plugin генерит interfaces + DTO в build/generated/openapi.
openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("$projectDir/src/main/resources/openapi/catalog.openapi.yaml")
    outputDir.set("$buildDir/generated/openapi")
    apiPackage.set("ru.remodov.catalog.generated.api")
    modelPackage.set("ru.remodov.catalog.generated.api.model")
    invokerPackage.set("ru.remodov.catalog.generated.api.invoker")
    generateApiTests.set(false)
    generateApiDocumentation.set(false)
    generateModelTests.set(false)
    generateModelDocumentation.set(false)
    configOptions.set(
        mapOf(
            "useSpringBoot3" to "true",
            "useJakartaEe" to "true",
            "interfaceOnly" to "true",
            "skipDefaultInterface" to "true",
            "useTags" to "true",
            "openApiNullable" to "false",
            "documentationProvider" to "none",
            "annotationLibrary" to "none",
            "dateLibrary" to "java8",
            "useBeanValidation" to "true"
        )
    )
}

sourceSets["main"].java.srcDir("$buildDir/generated/openapi/src/main/java")

tasks.named("compileJava") {
    dependsOn("openApiGenerate")
}

tasks.test {
    useJUnitPlatform()
}
