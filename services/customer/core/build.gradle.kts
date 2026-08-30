val dddBuildingBlocksVersion: String by rootProject
val usecasePatternVersion: String by rootProject

dependencies {
    implementation("ru.vikulinva:ddd-building-blocks:$dddBuildingBlocksVersion")
    implementation("ru.vikulinva:usecase-pattern:$usecasePatternVersion")
    implementation("ru.vikulinva:hexagonal-architecture-core:1.0.0")

    implementation("org.springframework:spring-context:6.2.1")
    implementation("org.springframework:spring-tx:6.2.1")

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.2")
}
