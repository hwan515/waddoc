import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

plugins {
    java
    id("org.springframework.boot") version "3.3.7" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

group = "com.waddoc"
version = "0.0.1-SNAPSHOT"

extra["springBootVersion"] = "3.3.7"

allprojects {
    group = "com.waddoc"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    the<DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:${property("springBootVersion")}")
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    configurations.configureEach {
        if (name == "compileOnly") {
            extendsFrom(configurations.getByName("annotationProcessor"))
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()

        if (project.findProperty("skipIntegrationTests") == "true") {
            filter {
                excludeTestsMatching("*IntegrationTest")
            }
        }
    }
}
