import org.jooq.meta.jaxb.Logging
import org.jooq.meta.jaxb.ForcedType
import org.jooq.meta.jaxb.Matchers
import org.jooq.meta.jaxb.MatchersTableType
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

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
    .orElse("jdbc:postgresql://localhost:5433/backoffice")
val pgUser = providers.gradleProperty("local.db.user").orElse("backoffice")
val pgPass = providers.gradleProperty("local.db.password").orElse("backoffice")

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation("org.liquibase:liquibase-core")
    implementation("org.postgresql:postgresql")

    implementation("ru.vikulinva:usecase-pattern:1.1.0")
    implementation("ru.vikulinva:usecase-pattern-starter:1.1.0")

    // Spring RestClient + Resilience4j для исходящих вызовов (Catalog, см. спека §11)
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    implementation("io.github.resilience4j:resilience4j-micrometer:2.2.0")
    implementation("org.springframework.boot:spring-boot-starter-aop")

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
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    // OpenAPI generator кладёт @Generated и @jakarta.annotation.* в сгенерённые stubs.
    implementation("jakarta.annotation:jakarta.annotation-api:3.0.0")
    implementation("io.swagger.core.v3:swagger-annotations:2.2.25")

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
                        packageName = "ru.remodov.backoffice.generated"
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

tasks.register("regenerate") {
    group = "build"
    description = "liquibase update + jooq generate from applied schema"
    dependsOn("update", "generateJooq")
    tasks.findByName("generateJooq")?.mustRunAfter("update")
}

// Inbound API: server-stubs для Operator BFF. Catalog-клиент добавляется на шаге ucp-integration-design.
openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("$projectDir/src/main/resources/openapi/backoffice.openapi.yaml")
    outputDir.set("$buildDir/generated/openapi")
    apiPackage.set("ru.remodov.backoffice.generated.api")
    modelPackage.set("ru.remodov.backoffice.generated.api.model")
    invokerPackage.set("ru.remodov.backoffice.generated.api.invoker")
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

// Outbound: Catalog Service client (spring RestClient через openapi-generator java/restclient library).
// См. resilience-style-guide R-RES-OAS-2.
tasks.register<GenerateTask>("generateCatalogClient") {
    generatorName.set("java")
    library.set("restclient")
    inputSpec.set("$projectDir/src/main/resources/openapi/catalog-client.openapi.yaml")
    outputDir.set("$buildDir/generated/openapi-catalog")
    apiPackage.set("ru.remodov.backoffice.catalog.generated.api")
    modelPackage.set("ru.remodov.backoffice.catalog.generated.api.model")
    invokerPackage.set("ru.remodov.backoffice.catalog.generated.api.invoker")
    generateApiTests.set(false)
    generateApiDocumentation.set(false)
    generateModelTests.set(false)
    generateModelDocumentation.set(false)
    configOptions.set(
        mapOf(
            "useJakartaEe" to "true",
            "openApiNullable" to "false",
            "dateLibrary" to "java8",
            "useBeanValidation" to "true",
            "performBeanValidation" to "false",
            "hideGenerationTimestamp" to "true",
            "annotationLibrary" to "swagger2"
        )
    )
}

sourceSets["main"].java.srcDir("$buildDir/generated/openapi-catalog/src/main/java")

tasks.named("compileJava") {
    dependsOn("openApiGenerate", "generateCatalogClient")
}

tasks.test {
    useJUnitPlatform()
    val resolvedDockerHost = System.getenv("DOCKER_HOST")
        ?: ("unix://" + System.getProperty("user.home") + "/.docker/run/docker.sock")
    environment("DOCKER_HOST", resolvedDockerHost)
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
}
