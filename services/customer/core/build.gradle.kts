val lombokVersion: String by rootProject
val dddBuildingBlocksVersion: String by rootProject
val usecasePatternVersion: String by rootProject

dependencies {
    implementation("ru.vikulinva:ddd-building-blocks:$dddBuildingBlocksVersion")
    implementation("ru.vikulinva:usecase-pattern:$usecasePatternVersion")
    implementation("ru.vikulinva:hexagonal-architecture-core:1.0.0")

    implementation("org.springframework:spring-context:6.2.1")
    implementation("org.springframework:spring-tx:6.2.1")

    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    testCompileOnly("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.2")
}
