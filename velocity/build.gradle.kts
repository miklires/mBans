plugins {
    java
    id("com.gradleup.shadow") version "9.0.0"
    id("com.modrinth.minotaur") version "2.9.0"
}

group = "io.github.miklires"
version = rootProject.version

modrinth {
    token.set(System.getenv("MODRINTH_TOKEN") ?: "")
    projectId.set(System.getenv("MODRINTH_PROJECT_ID") ?: "")
    versionNumber.set("${project.version}-velocity")
    versionName.set("mBans Velocity ${project.version}")
    versionType.set("release")
    uploadFile.set(tasks.shadowJar)
    gameVersions.add("26.2")
    loaders.add("velocity")
    changelog.set(provider { rootProject.file("CHANGELOG.md").readText() })
}

tasks.named("modrinth") {
    dependsOn(tasks.shadowJar)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("com.h2database:h2:2.3.232")
    implementation("com.mysql:mysql-connector-j:9.1.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.1")
    implementation("org.postgresql:postgresql:42.7.5")
    implementation("org.bstats:bstats-velocity:3.1.0")
    implementation("com.maxmind.geoip2:geoip2:5.2.0")
}

tasks {
    jar { archiveClassifier.set("plain") }
    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("mBans-Velocity-${project.version}.jar")
        mergeServiceFiles()
        relocate("com.zaxxer.hikari", "io.github.miklires.mbans.velocity.libs.hikari")
        relocate("org.bstats", "io.github.miklires.mbans.velocity.libs.bstats")
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
    build { dependsOn(shadowJar) }
}
