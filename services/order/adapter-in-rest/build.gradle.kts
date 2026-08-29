plugins {
    `java-library`
    alias(libs.plugins.openapi.generator)
}

dependencies {
    api(project(":core"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.usecase.pattern.starter)

    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)

    // Зависимости для сгенерированных DTO/контроллеров.
    implementation("io.swagger.core.v3:swagger-annotations:2.2.27")
    implementation("jakarta.annotation:jakarta.annotation-api:3.0.0")
    implementation("jakarta.validation:jakarta.validation-api:3.1.1")

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
}

// OpenAPI-first: генерация интерфейса контроллера + JsonBean DTO из YAML-спеки.
openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("$projectDir/src/main/resources/openapi/order-service.openapi.yaml")
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.path)
    apiPackage.set("ru.vikulinva.orderservice.adapter.in.rest.generated.api")
    modelPackage.set("ru.vikulinva.orderservice.adapter.in.rest.generated.model")
    invokerPackage.set("ru.vikulinva.orderservice.adapter.in.rest.generated")
    skipOverwrite.set(false)
    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "skipDefaultInterface" to "true",
            "useSpringBoot3" to "true",
            "useTags" to "true",
            "useJakartaEe" to "true",
            "openApiNullable" to "false",
            "performBeanValidation" to "true",
            "dateLibrary" to "java8",
            // JsonBean-style suffix через templates в будущем; сейчас стандартные имена.
        )
    )
}

sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("generated/openapi/src/main/java"))
        }
    }
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn("openApiGenerate")
}
