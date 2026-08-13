plugins {
    java
    id("com.gradleup.shadow") version "9.0.0"
}

group = "io.github.miklires"
version = rootProject.version

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
    implementation("com.mysql:mysql-connector-j:9.1.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.1")
    implementation("org.postgresql:postgresql:42.7.5")
    implementation("org.bstats:bstats-velocity:3.1.0")
}

tasks {
    jar { archiveClassifier.set("plain") }
    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("mBans-Velocity-${project.version}.jar")
        mergeServiceFiles()
        relocate("com.zaxxer.hikari", "io.github.miklires.mbans.velocity.libs.hikari")
        relocate("com.mysql", "io.github.miklires.mbans.velocity.libs.mysql")
        relocate("org.mariadb", "io.github.miklires.mbans.velocity.libs.mariadb")
        relocate("org.postgresql", "io.github.miklires.mbans.velocity.libs.postgresql")
        relocate("org.bstats", "io.github.miklires.mbans.velocity.libs.bstats")
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
    build { dependsOn(shadowJar) }
}
