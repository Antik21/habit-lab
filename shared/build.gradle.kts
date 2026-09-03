import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "com.denis.habitlab.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        androidResources {
            enable = true
        }
    }

    iosArm64()
    iosSimulatorArm64()
    // Apple Silicon runs the arm64 simulator; iosX64 is only configured on Intel hosts.
    // This avoids resolving an x64 simulator target during Apple Silicon checks while
    // retaining the target for Intel macOS contributors.
    if (HostManager.hostIsMac && HostManager.host.architecture == Architecture.X64) {
        iosX64()
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.resources)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
    }
}

private fun stripKotlinCommentsAndStrings(source: String): String {
    val result = StringBuilder(source.length)
    var index = 0
    var state = 0
    var blockCommentDepth = 0

    while (index < source.length) {
        val current = source[index]
        when (state) {
            0 -> when {
                source.startsWith("//", index) -> {
                    result.append("  ")
                    index += 2
                    state = 1
                }
                source.startsWith("/*", index) -> {
                    result.append("  ")
                    index += 2
                    blockCommentDepth = 1
                    state = 2
                }
                source.startsWith("\"\"\"", index) -> {
                    result.append("   ")
                    index += 3
                    state = 4
                }
                current == '\"' -> {
                    result.append(' ')
                    index += 1
                    state = 3
                }
                current == '\'' -> {
                    result.append(' ')
                    index += 1
                    state = 5
                }
                else -> {
                    result.append(current)
                    index += 1
                }
            }
            1 -> {
                result.append(if (current == '\n') '\n' else ' ')
                index += 1
                if (current == '\n') state = 0
            }
            2 -> when {
                source.startsWith("/*", index) -> {
                    result.append("  ")
                    index += 2
                    blockCommentDepth += 1
                }
                source.startsWith("*/", index) -> {
                    result.append("  ")
                    index += 2
                    blockCommentDepth -= 1
                    if (blockCommentDepth == 0) state = 0
                }
                else -> {
                    result.append(if (current == '\n') '\n' else ' ')
                    index += 1
                }
            }
            3, 5 -> when {
                current == '\\' && index + 1 < source.length -> {
                    result.append(' ')
                    index += 1
                    val escaped = source[index]
                    result.append(if (escaped == '\n') '\n' else ' ')
                    index += 1
                }
                (state == 3 && current == '\"') || (state == 5 && current == '\'') -> {
                    result.append(' ')
                    index += 1
                    state = 0
                }
                else -> {
                    result.append(if (current == '\n') '\n' else ' ')
                    index += 1
                }
            }
            else -> if (source.startsWith("\"\"\"", index)) {
                result.append("   ")
                index += 3
                state = 0
            } else {
                result.append(if (current == '\n') '\n' else ' ')
                index += 1
            }
        }
    }
    return result.toString()
}

private fun hasRootPackageContent(code: String): Boolean {
    var fileAnnotationNesting = 0

    code.lineSequence().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@forEach
        if (fileAnnotationNesting > 0) {
            fileAnnotationNesting += trimmed.count { it == '(' || it == '[' }
            fileAnnotationNesting -= trimmed.count { it == ')' || it == ']' }
            return@forEach
        }
        if (trimmed.startsWith("@file:")) {
            fileAnnotationNesting = trimmed.count { it == '(' || it == '[' }
            fileAnnotationNesting -= trimmed.count { it == ')' || it == ']' }
            return@forEach
        }
        if (trimmed.startsWith("package ") || trimmed.startsWith("import ")) return@forEach
        return true
    }
    return false
}

val checkArchitectureBoundaries by tasks.registering {
    group = "verification"
    description = "Verifies the shared commonMain package dependency boundaries."

    val commonKotlinSources = layout.projectDirectory.dir("src/commonMain/kotlin")
    inputs.dir(commonKotlinSources)

    doLast {
        val sharedRootPackage = "com.denis.habitlab.shared"
        val packageDeclaration = Regex("(?m)^\\s*package\\s+([A-Za-z0-9_.]+)")
        val sharedLayerReference = Regex("com\\.denis\\.habitlab\\.shared\\.(app|core|data|di|domain|presentation)(?:\\.|\\b)")
        val allowedDependencies = mapOf(
            "core" to emptySet<String>(),
            "domain" to setOf("core"),
            "data" to setOf("core", "domain"),
            "presentation" to setOf("core", "domain"),
            "app" to setOf("presentation"),
            "di" to setOf("app", "core", "data", "domain", "presentation"),
        )
        val coreOrDomainInfrastructure = Regex(
            "(?<![A-Za-z0-9_.])(?:android\\.|androidx\\.|platform\\.|java\\.|kotlinx\\.cinterop\\.|kotlin\\.native\\.|org\\.jetbrains\\.compose\\.|org\\.orbit\\.|org\\.koin\\.|io\\.ktor\\.|okhttp3\\.|app\\.cash\\.sqldelight\\.|io\\.realm\\.|sqlite\\.)",
        )
        val presentationInfrastructure = Regex(
            "(?<![A-Za-z0-9_.])(?:android\\.|androidx\\.(?!compose\\.)|platform\\.|java\\.|kotlinx\\.cinterop\\.|kotlin\\.native\\.|org\\.koin\\.|io\\.ktor\\.|okhttp3\\.|app\\.cash\\.sqldelight\\.|io\\.realm\\.|sqlite\\.)",
        )
        val dataSourceOrDaoReference = Regex(
            "(?im)(?:^\\s*import\\s+[A-Za-z0-9_.]*[A-Za-z_][A-Za-z0-9_]*(?:dao|datasource)\\b|(?:[A-Za-z_][A-Za-z0-9_]*\\.)+[A-Za-z_][A-Za-z0-9_]*(?:dao|datasource)\\b|^\\s*(?:(?:public|internal|private)\\s+)?(?:class|interface|object)\\s+[A-Za-z_][A-Za-z0-9_]*(?:dao|datasource)\\b)",
        )
        val koinReference = Regex("(?<![A-Za-z0-9_.])org\\.koin\\.")
        val violations = mutableListOf<String>()

        fileTree(commonKotlinSources) { include("**/*.kt") }.files.sorted().forEach { file ->
            val code = stripKotlinCommentsAndStrings(file.readText())
            val packageName = packageDeclaration.find(code)?.groupValues?.get(1) ?: return@forEach
            val relativePath = file.relativeTo(project.projectDir).path

            if (packageName == sharedRootPackage) {
                if (hasRootPackageContent(code)) {
                    violations += "$relativePath: shared root package is reserved for package-only files"
                }
                return@forEach
            }

            val layer = listOf("app", "core", "data", "di", "domain", "presentation")
                .firstOrNull { packageName.startsWith("$sharedRootPackage.$it.") || packageName == "$sharedRootPackage.$it" }
            if (layer == null) {
                if (packageName.startsWith("$sharedRootPackage.")) {
                    violations += "$relativePath: package must belong to app, core, data, di, domain, or presentation"
                }
                return@forEach
            }

            val referencedLayers = sharedLayerReference.findAll(code)
                .map { it.groupValues[1] }
                .filter { it != layer }
                .toSortedSet()
            referencedLayers.filterNot { it in allowedDependencies.getValue(layer) }.forEach { dependency ->
                violations += "$relativePath: $layer may not depend on shared.$dependency"
            }

            if (layer == "core" && coreOrDomainInfrastructure.containsMatchIn(code)) {
                violations += "$relativePath: core must not use UI, DI, database, network, or platform SDKs"
            }
            if (layer == "domain" && coreOrDomainInfrastructure.containsMatchIn(code)) {
                violations += "$relativePath: domain must remain pure common Kotlin"
            }
            if (layer == "presentation" && presentationInfrastructure.containsMatchIn(code)) {
                violations += "$relativePath: presentation must not use native or infrastructure APIs"
            }
            if (layer == "presentation" && dataSourceOrDaoReference.containsMatchIn(code)) {
                violations += "$relativePath: presentation must not reference DAO or DataSource types"
            }
            if (layer != "di" && koinReference.containsMatchIn(code)) {
                violations += "$relativePath: org.koin references are only allowed in di"
            }
        }

        val report = violations.joinToString(separator = "\n")
        check(violations.isEmpty()) {
            "Architecture boundary violations:\n$report"
        }
    }
}

tasks.named("check") {
    dependsOn(checkArchitectureBoundaries)
}
