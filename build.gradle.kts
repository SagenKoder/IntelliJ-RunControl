plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.10.4"
}

group = "app.sagen"
version = "1.0.2"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("org.eclipse.jetty:jetty-server:11.0.20")
    implementation("org.eclipse.jetty:jetty-servlet:11.0.20")
    implementation("jakarta.servlet:jakarta.servlet-api:6.1.0")
    implementation("com.google.code.gson:gson:2.10.1")

    intellijPlatform {
        intellijIdeaCommunity("2024.1")
    }
}

intellijPlatform {
    pluginConfiguration {
        version = "1.0.2"
        ideaVersion {
            sinceBuild = "241"
            untilBuild = "253.*"
        }
    }

    pluginVerification {
        ides {
            ide("IC-2024.1.6")
            ide("IC-2024.2.4")
        }
    }

    signing {
        // Configure via environment variables or gradle.properties
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // Optional: Use channels for staged releases
        // channels = listOf("beta")
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
}

kotlin {
    jvmToolchain(17)
}
