plugins {
    java
    id("com.azuredoom.hytale-tools")
}

group = project.property("group").toString()
version = project.property("version").toString()

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(property("java_version").toString().toInt()))
}

repositories {
    mavenCentral()
}

dependencies {
    // The API is part of this plugin's own jar (see the `jar` task below),
    // not an external runtime dependency.
    implementation(project(":api"))
}

hytaleTools {
    // hytaleVersion / patchline / manifestGroup are inherited from hytaleWorkspace.
    javaVersion = property("java_version").toString().toInt()
    manifestServerVersion = property("manifestServerVersion").toString()
    modId = property("mod_id").toString()
    modDescription = property("mod_description").toString()
    modUrl = property("mod_url").toString()
    mainClass = property("main_class").toString()
    modCredits = property("mod_author").toString()
    manifestDependencies = property("manifest_dependencies").toString()
    manifestOptionalDependencies = property("manifest_opt_dependencies").toString()
    curseforgeId = property("curseforgeID").toString()
    disabledByDefault = property("disabled_by_default").toString().toBoolean()
    includesPack = property("includes_pack").toString().toBoolean()
    injectServerJavadocsIntoSources = property("injectServerJavadocsIntoSources").toString().toBoolean()
    generateAssetsBinary = property("generateAssetsBinary").toString().toBoolean()

    // Optional, machine-specific: point at a local Hytale install to reuse its
    // Assets.zip instead of running the OAuth device flow. Set it in
    // ~/.gradle/gradle.properties, never here -- the path is not portable.
    findProperty("hytaleHomeOverride")?.toString()?.takeIf { it.isNotBlank() }?.let {
        hytaleHomeOverride = it
    }
}

tasks.withType<Javadoc>().configureEach {
    (options as org.gradle.external.javadoc.StandardJavadocDocletOptions)
        .addStringOption("Xdoclint:-missing", "-quiet")
}

// Ship a single jar: API classes are bundled alongside the implementation so
// the server sees one plugin, while `talent-graph-api` stays publishable alone.
tasks.named<Jar>("jar") {
    archiveBaseName.set(project.property("mod_name").toString())
    archiveVersion.set(project.property("version").toString())
    from(project(":api").sourceSets["main"].output)
}
