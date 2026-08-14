plugins {
    java
    id("com.gradleup.shadow") version "9.0.0"
    id("com.modrinth.minotaur") version "2.9.0"
}

group = "io.github.miklires"
version = "1.0.0"

modrinth {
    token.set(System.getenv("MODRINTH_TOKEN") ?: "")
    projectId.set(System.getenv("MODRINTH_PROJECT_ID") ?: "")
    versionNumber.set(project.version.toString())
    versionName.set("mBans ${project.version}")
    versionType.set("release")
    uploadFile.set(tasks.shadowJar)
    additionalFiles {
        other(layout.projectDirectory.file("velocity/build/libs/mBans-Velocity-${project.version}.jar"))
    }
    gameVersions.add("26.2")
    loaders.addAll("paper", "purpur", "folia", "velocity")
    changelog.set(provider { file("CHANGELOG.md").takeIf { it.exists() }?.readText() ?: "" })
    syncBodyFrom.set(provider { file("README.md").takeIf { it.exists() }?.readText() ?: "" })
}

tasks.named("modrinth") {
    dependsOn(":velocity:shadowJar")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.112-stable")
    compileOnly("me.clip:placeholderapi:2.11.7")
    implementation("org.bstats:bstats-bukkit:3.1.0")
    implementation("com.google.code.gson:gson:2.14.0")
    compileOnly("com.maxmind.geoip2:geoip2:5.2.0")

    compileOnly("com.zaxxer:HikariCP:6.2.1")
    compileOnly("com.h2database:h2:2.3.232")
    compileOnly("org.xerial:sqlite-jdbc:3.47.1.0")
    compileOnly("com.mysql:mysql-connector-j:9.1.0")
    compileOnly("org.mariadb.jdbc:mariadb-java-client:3.5.1")
    compileOnly("org.postgresql:postgresql:42.7.5")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("com.h2database:h2:2.3.232")
}

tasks {
    jar {
        archiveClassifier.set("plain")
    }

    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("mBans-${project.version}.jar")

        exclude("META-INF/LICENSE*")
        exclude("META-INF/NOTICE*")
        exclude("META-INF/DEPENDENCIES")
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("META-INF/versions/**/module-info.class")
        exclude("module-info.class")

        mergeServiceFiles()
        relocate("org.bstats", "io.github.miklires.mbans.libs.bstats")
        relocate("com.google.gson", "io.github.miklires.mbans.libs.gson")
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }

    test {
        useJUnitPlatform()
    }
}
