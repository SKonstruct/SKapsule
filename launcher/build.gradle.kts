plugins {
    // Kotlin is provided by AGP 9's built-in Kotlin support (KGP pinned by AGP),
    // so we no longer apply org.jetbrains.kotlin.android ourselves.
    id("com.android.application") version "9.2.0" apply false
    id("com.diffplug.spotless") version "6.25.0"
}

spotless {
    java {
        target(
            "../external/bootstrap/src/**/*.java",
            "../external/frenchpress/src/**/*.java",
        )
        // Google Java Format with AOSP style (which is closer to standard spacing)
        googleJavaFormat().aosp()
    }

    kotlin {
        target("**/*.kt", "**/*.kts")
        targetExclude("**/build/**", "**/out/**", "**/external/**")
        ktlint().editorConfigOverride(
            mapOf(
                "standard:max-line-length" to "disabled",
                "standard:if-else-wrapping" to "disabled",
                "standard:value-argument-comment" to "disabled",
                "standard:value-parameter-comment" to "disabled",
                "standard:no-consecutive-comments" to "disabled",
            ),
        )
    }

    format("misc") {
        target("**/*.xml", "**/*.json", "**/*.yml", "**/*.yaml", "**/*.md")
        targetExclude("**/build/**", "**/out/**", "**/external/**")
        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()
    }
}
