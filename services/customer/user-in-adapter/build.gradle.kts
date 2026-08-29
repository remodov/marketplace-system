plugins {
    id("org.openapi.generator") version "7.10.0"
}

val lombokVersion: String by rootProject
val mapstructVersion: String by rootProject
val springBootVersion: String by rootProject

dependencies {
    implementation(project(":core"))

    implementation("ru.vikulinva:usecase-pattern:1.1.0")
    implementation("ru.vikulinva:ddd-building-blocks:1.0.0")

    implementation("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-validation:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-security:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server:$springBootVersion")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    implementation("org.mapstruct:mapstruct:$mapstructVersion")
    annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    testCompileOnly("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
    testImplementation("org.springframework.security:spring-security-test:6.4.2")
}

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("$projectDir/src/main/resources/openapi/customer.openapi.yaml")
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.absolutePath)
    apiPackage.set("ru.vikulinva.customer.generated.api")
    modelPackage.set("ru.vikulinva.customer.generated.api.model")
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
            "dateLibrary" to "java8"
        )
    )
}

sourceSets.named("main") {
    java.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/java"))
}

tasks.named("compileJava") {
    dependsOn("openApiGenerate")
}

tasks.named("checkstyleMain") {
    enabled = false
}
