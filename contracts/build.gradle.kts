// Контракты событий: DTO не пишутся руками, а генерируются из схемы.
//
// AsyncAPI-документ (src/main/resources/asyncapi) описывает каналы, заголовки и
// сообщения; поля payload лежат отдельным файлом схем, и по нему генератор делает
// Java-классы. Продюсер и консьюмер зависят от этого модуля, поэтому расхождение
// вроде «customerId уехал объектом, а читают строкой» ловится компилятором.
plugins {
    `java-library`
    id("org.openapi.generator") version "7.10.0"
}

group = "ru.vikulinva.marketplace"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-annotations:2.18.2")
    api("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")
    // Генератор проставляет @Generated и @Nonnull — аннотации нужны на компиляции.
    api("jakarta.annotation:jakarta.annotation-api:3.0.0")
    compileOnly("io.swagger.core.v3:swagger-annotations:2.2.25")
    compileOnly("jakarta.validation:jakarta.validation-api:3.1.0")
    // @DateTimeFormat в сгенерированных моделях: контракт не тянет весь Spring,
    // достаточно модуля с аннотациями формата.
    api("org.springframework:spring-context:6.2.1")
}

openApiGenerate {
    // Генератор spring, а не java+native: тот подмешивает в модели ApiClient для
    // сборки query-строки, а клиента мы не генерируем — только DTO.
    generatorName.set("spring")
    inputSpec.set("$projectDir/src/main/resources/schemas/order-events.yaml")
    outputDir.set("${layout.buildDirectory.get()}/generated/contracts")
    modelPackage.set("ru.vikulinva.marketplace.contracts.orders.v1")
    globalProperties.set(mapOf("models" to "", "modelDocs" to "false", "modelTests" to "false"))
    configOptions.set(mapOf(
        "useJakartaEe" to "true",
        "useSpringBoot3" to "true",
        "serializationLibrary" to "jackson",
        "openApiNullable" to "false",
        "hideGenerationTimestamp" to "true",
    ))
}

sourceSets["main"].java.srcDir("${layout.buildDirectory.get()}/generated/contracts/src/main/java")

tasks.named("compileJava") { dependsOn("openApiGenerate") }
