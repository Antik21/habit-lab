package com.denis.habitlab.buildlogic

data class ArchitectureSource(
    val relativePath: String,
    val content: String,
)

/**
 * Pure common-source boundary checker. Call [findViolations] with synthetic sources in build-logic tests.
 */
class ArchitectureBoundaryChecker {
    fun findViolations(sources: Iterable<ArchitectureSource>): List<String> {
        return sources
            .sortedBy { it.relativePath }
            .flatMap { source -> findViolations(source) }
    }

    private fun findViolations(source: ArchitectureSource): List<String> {
        val code = stripKotlinCommentsAndStrings(source.content)
        val packageName = packageDeclaration.find(code)?.groupValues?.get(1)
            ?: return listOf("${source.relativePath}: Kotlin source must declare a package")
        val violations = mutableListOf<String>()
        val isDiPackage = packageName == "$sharedRootPackage.di" ||
            packageName.startsWith("$sharedRootPackage.di.")

        if (!isDiPackage && koinReference.containsMatchIn(code)) {
            violations += "${source.relativePath}: org.koin references are only allowed in di"
        }

        if (packageName == sharedRootPackage) {
            if (hasRootPackageContent(code)) {
                violations += "${source.relativePath}: shared root package is reserved for package-only files"
            }
            return violations
        }

        val layer = layers.firstOrNull {
            packageName.startsWith("$sharedRootPackage.$it.") || packageName == "$sharedRootPackage.$it"
        }
        if (layer == null) {
            if (packageName.startsWith("$sharedRootPackage.")) {
                violations += "${source.relativePath}: package must belong to app, core, data, di, domain, or presentation"
            }
            return violations
        }

        val referencedLayers = sharedLayerReference.findAll(code)
            .map { it.groupValues[1] }
            .filter { it != layer }
            .toSortedSet()
        referencedLayers.filterNot { it in allowedDependencies.getValue(layer) }.forEach { dependency ->
            violations += "${source.relativePath}: $layer may not depend on shared.$dependency"
        }

        if (layer == "core" && coreOrDomainInfrastructure.containsMatchIn(code)) {
            violations += "${source.relativePath}: core must not use UI, DI, database, network, or platform SDKs"
        }
        if (layer == "domain" && coreOrDomainInfrastructure.containsMatchIn(code)) {
            violations += "${source.relativePath}: domain must remain pure common Kotlin"
        }
        if (layer == "presentation" && presentationInfrastructure.containsMatchIn(code)) {
            violations += "${source.relativePath}: presentation must not use native or infrastructure APIs"
        }
        if (layer == "presentation" && dataSourceOrDaoReference.containsMatchIn(code)) {
            violations += "${source.relativePath}: presentation must not reference DAO or DataSource types"
        }
        return violations
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

    private companion object {
        const val sharedRootPackage = "com.denis.habitlab.shared"
        val layers = listOf("app", "core", "data", "di", "domain", "presentation")
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
            "\\b[A-Z][A-Za-z0-9_]*(?i:Dao|DataSource)\\b",
        )
        val koinReference = Regex("(?<![A-Za-z0-9_.])org\\.koin\\.")
    }
}
