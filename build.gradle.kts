plugins {
    `groovy`
    `java-gradle-plugin`
    `maven-publish`
}

group = "com.yourorg.openapi"
version = "1.0.0-SNAPSHOT"

gradlePlugin {
    plugins {
        create("openapiFlattener") {
            id = "com.yourorg.openapi-flattener"
            implementationClass = "com.yourorg.openapi.OpenApiFlattenerPlugin"
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.yaml:snakeyaml:2.2")
}
