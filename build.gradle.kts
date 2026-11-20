plugins {
    java
}

group = "local.blockintegrations"
version = "2.44.2-26.2"

val vystormCoreJar = providers.gradleProperty("vystormCoreJar")
    .orElse("libs/Vystorm_Core.jar")

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly(files(vystormCoreJar))
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation(files(vystormCoreJar))
    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.20.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        resources {
            setSrcDirs(listOf("."))
            include("plugin.yml", "config.yml", "setup.yml", "tutorial.yml", "tutorial_*.yml", "lang/*.yml", "LICENSE")
        }
    }
    test {
        java.setSrcDirs(listOf("tests"))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") { expand("version" to project.version) }
}

tasks.jar {
    archiveFileName = "BlockIntegrations-${project.version}.jar"
}

val integration by sourceSets.creating {
    java.srcDir("integration/src")
    resources.srcDir("integration/resources")
    compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
}

tasks.register<Jar>("integrationJar") {
    dependsOn(tasks.named(integration.classesTaskName))
    from(integration.output)
    archiveFileName = "BlockIntegrationsPortabilityAcceptance.jar"
}


tasks.compileJava {
    doFirst {
        require(file(vystormCoreJar.get()).isFile) { "Vystorm Core jar (0.65.0+) missing: put it at libs/Vystorm_Core.jar or pass -PvystormCoreJar=/path/to/Vystorm_Core.jar" }
    }
}

val paper1206Libraries = providers.gradleProperty("paper1206Libraries")
    .orElse((System.getProperty("user.home") + "/source/tools/smoke-1206/libraries"))
val compat1206 by tasks.registering(JavaCompile::class) {
    description = "Compiles main sources against the offline Paper 1.20.6 libraries."
    source = sourceSets.main.get().java
    classpath = files(fileTree(paper1206Libraries.get()) { include("**/*.jar") }, vystormCoreJar)
    destinationDirectory = layout.buildDirectory.dir("classes/compat1206")
    javaCompiler = javaToolchains.compilerFor { languageVersion = JavaLanguageVersion.of(25) }
    options.release = 21
}
tasks.check { dependsOn(compat1206) }
val modern by sourceSets.creating {
    java.setSrcDirs(listOf("modern"))
    compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
}
sourceSets.test { compileClasspath += modern.output; runtimeClasspath += modern.output }
tasks.jar { from(modern.output) }

val test1206 by tasks.registering(Test::class) {
    description = "Runs compatibility selection and linkage tests with the real 1.20.6 API."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = files(fileTree(paper1206Libraries.get()) { include("**/*.jar") }) +
        sourceSets.test.get().runtimeClasspath.filter { !it.name.startsWith("paper-api-") && !it.name.startsWith("adventure-") }
    useJUnitPlatform()
    include("**/CompatibilityTest.class")
    systemProperty("compat1206", "true")
}
tasks.check { dependsOn(test1206) }
