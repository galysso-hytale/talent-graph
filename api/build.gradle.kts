// Public API of TalentGraph. Published standalone so third-party plugins can
// compile against it without depending on the implementation.
//
// Deliberate constraint: this module has NO dependency on the Hytale server.
// It must stay compilable against the JDK alone. See CONTRIBUTING notes in
// README.md for the rationale and the escape hatch.
plugins {
    `java-library`
    `maven-publish`
}

group = project.property("group").toString()
version = project.property("version").toString()

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(property("java_version").toString().toInt()))
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

tasks.withType<Javadoc>().configureEach {
    (options as org.gradle.external.javadoc.StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:all,-missing", "-quiet")
        // Standard doclet does not register these by default.
        tags(
            "apiNote:a:API Note:",
            "implSpec:a:Implementation Requirements:",
            "implNote:a:Implementation Note:",
        )
    }
}

// Covers jar, sourcesJar and javadocJar so every published artifact agrees
// with the `artifactId` below.
tasks.withType<Jar>().configureEach {
    archiveBaseName.set("talent-graph-api")
}

publishing {
    publications {
        create<MavenPublication>("api") {
            artifactId = "talent-graph-api"
            from(components["java"])
        }
    }
}
