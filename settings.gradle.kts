pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
        }
        // GitHub Packages (uncomment and configure for publishing)
        maven {
            url = uri("https://maven.pkg.github.com/YOUR_GITHUB_USERNAME/voice-agent-kit")
             credentials {
                 username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("chaithu-30")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("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")
           }
         }
    }
}

rootProject.name = "voice-agent-kit"
include(":voice-agent-kit")
include(":sample-app")
