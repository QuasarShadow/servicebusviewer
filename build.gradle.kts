plugins {
    java
    application
    id("org.javamodularity.moduleplugin") version "1.8.15"
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.beryx.jlink") version "3.1.3"
}

group = "com.dutils.servicebusviewer"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()

}

val junitVersion = "5.12.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

application {
    mainModule.set("com.dutils.servicebusviewer")
    mainClass.set("com.dutils.servicebusviewer.ServiceBusViewerApplication")
}

javafx {
    version = "25"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.web", "javafx.media","javafx.graphics")
}

dependencies {
    implementation("org.controlsfx:controlsfx:11.2.1")
    implementation("org.apache.commons:commons-collections4:4.5.0")
    implementation("org.apache.commons:commons-lang3:3.19.0")
    implementation("org.fxmisc.richtext:richtextfx:0.11.6")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    testImplementation("org.junit.jupiter:junit-jupiter-api:${junitVersion}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${junitVersion}")

    implementation("io.github.mkpaz:atlantafx-base:2.1.0")
    implementation(platform("org.kordamp.ikonli:ikonli-bom:12.4.0"))
    implementation(platform("com.azure:azure-sdk-bom:1.3.0"))

    implementation("com.azure:azure-messaging-servicebus")
    implementation("com.azure:azure-identity")

    implementation("com.microsoft.azure:msal4j")

    implementation("org.kordamp.ikonli:ikonli-javafx")
    implementation("org.kordamp.ikonli:ikonli-fontawesome6-pack")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register("dist") {
    group = "distribution"
    dependsOn("clean", "jlinkZip")
    description = "Calls clean and then jlinkZip [default]"
}


jlink {
    imageZip.set(layout.buildDirectory.file("distributions/app-${javafx.platform.classifier}.zip"))
    options.set(
        listOf(
            "--bind-services",
            "--strip-debug",
            "--compress",
            "2",
            "--no-header-files",
            "--no-man-pages",
            "--ignore-signing-information"
        )
    )
    addExtraDependencies("javafx")

    mergedModule {
        requires("java.logging")
        requires("java.desktop")
        requires("jdk.unsupported")
        requires("javafx.graphics")
        requires("javafx.controls")
    }

    // Merge additional non-modular libs to avoid split packages and module inference issues
    forceMerge(
        "netty",
        "tcnative",
        "io.netty.tcnative.classes.openssl",
        "reactor",
        "reactor-netty",
        "slf4j",
        "micrometer",
        "brotli4j",
        "zstd",
        "protobuf",
        "jna",
        "qpid",
        "proton",
        "msal4j",
        "reactfx",
        "flowless",
        "undofx",
        "com.microsoft"
    )

    launcher {
        name = "servicebusviewer"
        jvmArgs = listOf<String>("-Xmx3024m","--enable-native-access=ALL-UNNAMED")

    }

    jpackage {
        val rawVersion = project.version.toString()
        val sanitizedVersion = rawVersion
            .replace("-SNAPSHOT", "")
            .replace(Regex("[^0-9.]"), ".")
            .replace(Regex("\\.+"), ".")
            .trim('.')
            .ifBlank { "1.0.0" }
        appVersion = sanitizedVersion
        vendor = "dutils"
        installerType = "pkg"
        val iconFile = file("src/main/resources/icons/sbinspect.icns")
        if (iconFile.exists()) {
            imageOptions = listOf("--icon", iconFile.path)
        }
        outputDir = layout.buildDirectory.dir("jpackage").get().asFile.toString()
    }
}