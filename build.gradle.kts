plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.2.2"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

version = providers.gradleProperty("version").getOrElse(version.toString())

repositories {
    mavenCentral()
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.onarandombox.com/content/groups/public")
    maven("https://jitpack.io")
    maven("https://mvn.wesjd.net/")
}

dependencies {
    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("net.wesjd:anvilgui:1.10.13-SNAPSHOT")
    runtimeOnly("com.mysql:mysql-connector-j:9.5.0")
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("net.luckperms:api:5.5")
    compileOnly("org.mvplugins.multiverse.core:multiverse-core:5.6.2")
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Core:2.12.0")
    testImplementation("io.papermc.paper:paper-api:26.1.2.build.+")
    testImplementation("com.github.MilkBowl:VaultAPI:1.7.1")
    testImplementation("net.luckperms:api:5.5")
    testImplementation("org.mvplugins.multiverse.core:multiverse-core:5.6.2")
    testImplementation("com.fastasyncworldedit:FastAsyncWorldEdit-Core:2.12.0")
    testImplementation("com.h2database:h2:2.4.240")
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
        manifest {
            attributes["paperweight-mappings-namespace"] = "spigot"
        }
    }

    jar {
        enabled = false
    }
}
