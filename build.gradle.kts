import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()
repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
    maven("https://www.jetbrains.com/intellij-repository/releases")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    maven("https://maven.aliyun.com/nexus/content/groups/public/")
    maven("https://maven.aliyun.com/nexus/content/repositories/central/")
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion").get())
        bundledPlugin("com.intellij.java")
        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.JUnit5)
    }

    // SQLite JDBC 驱动（排除自带 slf4j，使用 IntelliJ Platform 内置版本）
    implementation("org.xerial:sqlite-jdbc:3.45.1.0") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    // Gson JSON 处理（extConfig 序列化/反序列化、模板导入导出）
    implementation("com.google.code.gson:gson:2.10.1")
}
intellijPlatform {
    pluginConfiguration {
        id = providers.gradleProperty("pluginId").get()
        name = providers.gradleProperty("pluginName").get()
        version = providers.gradleProperty("pluginVersion").get()

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild").get()
            untilBuild = providers.gradleProperty("pluginUntilBuild").get()
        }

        vendor {
            name = "wp2code"
            url = "https://github.com/wp2code/idea-plugin"
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }

    publishing {
        token = System.getenv("PUBLISH_TOKEN")
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
    test {
        useJUnitPlatform()
    }
    runIde {
        // 增大沙盒 IDEA 的 JVM 堆内存，避免 debug 时 OOM
        jvmArgs("-Xms512m", "-Xmx2048m", "-XX:+UseG1GC", "-XX:ReservedCodeCacheSize=512m")
    }
}
