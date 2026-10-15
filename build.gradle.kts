plugins {
    id("java-library")
    `maven-publish`
}

group = "cn.enaium.fabricpvz"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// This module is the only Fabric-aware piece of the port: it implements the real Fabric Loader's
// game provider SPI for the PvZ port, so that `java -cp <game+loader+provider+mods> \
// net.fabricmc.loader.impl.launch.knot.KnotClient` boots the port with a `mods/` directory.
//
// The game itself stays free of any Fabric dependency: this module never touches game classes at
// compile time (it knows the game only by class name and by scanning the class path), so the game
// can depend on this module for its test/runtime launches without a cycle.
dependencies {
    // The SPI this class implements (net.fabricmc.loader.impl.game.GameProvider). compileOnly: the
    // loader is provided by the launcher, and publishing it as a compile dependency would put the
    // loader into every consumer's compile class path.
    compileOnly(libs.fabric.loader)

    // The launch libraries: the real Fabric Loader plus the Mixin and ASM versions its own manifest
    // (fabric-loader-0.19.5.json) ships. Published as runtime dependencies, so depending on this
    // artifact is enough to launch the game through the real loader.
    runtimeOnly(libs.fabric.loader)
    runtimeOnly(libs.sponge.mixin)
    runtimeOnly(libs.asm)
    runtimeOnly(libs.asm.analysis)
    runtimeOnly(libs.asm.commons)
    runtimeOnly(libs.asm.tree)
    runtimeOnly(libs.asm.util)

    // The logging stack every launch of this workspace runs on: the SLF4J API (the facade the
    // loader, the Mixin subsystem and every mod log through) with the Log4j backend as its only
    // implementation. It is a launch library exactly like Mixin and ASM - the loader itself logs
    // through net.fabricmc.loader.impl.util.log, which this provider points at the SLF4J facade of
    // this stack (see PvzGameProvider), and a mod only ever compiles against org.slf4j (the loom
    // plugin's compileOnly entry). log4j-slf4j2-impl brings log4j-api and slf4j-api with it.
    //
    // The configuration file (log4j2.xml, next to this class) is a resource of this artifact, so
    // every launch path - gradlew run, a mod's runClient, the end-to-end forks, an installed game -
    // gets the same format without configuring anything.
    runtimeOnly(libs.log4j.core)
    runtimeOnly(libs.log4j.slf4j2.impl)

    // The Kotlin standard library is deliberately NOT here: the runtime of the Kotlin mods is
    // fabric-language-kotlin's (the mod's nested kotlin-stdlib, added to the loader's class path
    // when the loader loads that mod), so the workspace has exactly one owner of kotlin.* - the real
    // language module - instead of a second copy here shadowing it. See the loom plugin, which
    // ships that mod, and docs/VERIFICATION.md, section 35.

    testImplementation(libs.fabric.loader)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

tasks.jar {
    archiveBaseName = "fabric-provider"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "cn.enaium.fabricpvz"
            artifactId = "fabric-provider"
            version = "1.0.0"
            pom {
                name.set("fabric-provider")
                description.set("Fabric Loader game provider (GameProvider SPI) for the PvZ-Portable Java port")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
}
