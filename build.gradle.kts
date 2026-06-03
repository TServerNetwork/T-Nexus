plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.2.2"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

version = providers.gradleProperty("version").getOrElse(version.toString())

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    testImplementation("io.papermc.paper:paper-api:26.1.2.build.+")
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v26.1.2:4.113.1")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(26)
}

tasks {
    assemble {
        dependsOn(shadowJar)
    }

    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("26.1.2")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version, "description" to project.description)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    test {
        useJUnitPlatform()
    }

    shadowJar {
        archiveClassifier.set("")
    }

    jar {
        enabled = false
    }
}
