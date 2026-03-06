plugins {
    id("java")
}

group = "me.itstautvydas"
version = "1.2.0-folia"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT")
    compileOnly("org.apache.logging.log4j:log4j-core:2.22.1")
}

tasks.jar {
    archiveBaseName.set("VelocityCommandForward")
    archiveVersion.set(version.toString())
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to version)
    }
}
