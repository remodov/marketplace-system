plugins {
    id("nu.studer.jooq") version "10.0"
}

val lombokVersion: String by rootProject
val mapstructVersion: String by rootProject
val jooqVersion: String by rootProject
val liquibaseVersion: String by rootProject
val postgresVersion: String by rootProject
val springBootVersion: String by rootProject
val testcontainersVersion: String by rootProject

dependencies {
    implementation(project(":core"))

    implementation("ru.vikulinva:ddd-building-blocks:1.0.0")

    implementation("org.springframework.boot:spring-boot-starter-jooq:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-jdbc:$springBootVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("org.liquibase:liquibase-core:$liquibaseVersion")
    implementation("org.jooq:jooq:$jooqVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    implementation("org.mapstruct:mapstruct:$mapstructVersion")
    annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    testCompileOnly("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")

    jooqGenerator("org.postgresql:postgresql:$postgresVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
}

jooq {
    version.set(jooqVersion)
    configurations {
        create("main") {
            jooqConfiguration.apply {
                jdbc.apply {
                    driver = "org.postgresql.Driver"
                    url = System.getenv("CUSTOMER_DB_URL") ?: "jdbc:postgresql://localhost:5434/customer"
                    user = System.getenv("CUSTOMER_DB_USER") ?: "customer"
                    password = System.getenv("CUSTOMER_DB_PASSWORD") ?: "customer"
                }
                generator.apply {
                    name = "org.jooq.codegen.DefaultGenerator"
                    database.apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = "public"
                        excludes = "databasechangelog|databasechangeloglock"
                        forcedTypes.add(
                            org.jooq.meta.jaxb.ForcedType()
                                .withName("VARCHAR")
                                .withIncludeTypes("citext")
                        )
                    }
                    target.apply {
                        packageName = "ru.vikulinva.customer.persistence.generated"
                        directory = layout.buildDirectory.dir("generated/jooq").get().asFile.absolutePath
                    }
                    generate.apply {
                        isPojos = true
                        isRecords = true
                        isFluentSetters = false
                        isDeprecated = false
                    }
                }
            }
        }
    }
}

tasks.named("checkstyleMain") {
    enabled = false
}
