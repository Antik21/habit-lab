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

        if (!isDiPackage && !isKoinCompositionRoot(source, packageName) && koinReference.containsMatchIn(code)) {
            violations += "${source.relativePath}: org.koin references are only allowed in di"
        }

        if (packageName != sharedRootPackage && !packageName.startsWith("$sharedRootPackage.")) {
            violations += "${source.relativePath}: package must be under $sharedRootPackage"
            return violations
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
        if (
            layer == "presentation" &&
            !isLifecyclePresentationEntry(source, packageName) &&
            presentationInfrastructure.containsMatchIn(code)
        ) {
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
        val returnStates = ArrayDeque<Int>()
        val templateBraceDepths = ArrayDeque<Int>()

        while (index < source.length) {
            val current = source[index]
            when (state) {
                0 -> when {
                    source.startsWith("//", index) -> {
                        result.append("  ")
                        index += 2
                        returnStates.addLast(state)
                        state = 1
                    }
                    source.startsWith("/*", index) -> {
                        result.append("  ")
                        index += 2
                        blockCommentDepth = 1
                        returnStates.addLast(state)
                        state = 2
                    }
                    source.startsWith("\"\"\"", index) -> {
                        result.append("   ")
                        index += 3
                        returnStates.addLast(state)
                        state = 4
                    }
                    current == '\"' -> {
                        result.append(' ')
                        index += 1
                        returnStates.addLast(state)
                        state = 3
                    }
                    current == '\'' -> {
                        result.append(' ')
                        index += 1
                        returnStates.addLast(state)
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
                    if (current == '\n') state = returnStates.removeLast()
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
                        if (blockCommentDepth == 0) state = returnStates.removeLast()
                    }
                    else -> {
                        result.append(if (current == '\n') '\n' else ' ')
                        index += 1
                    }
                }
                3 -> when {
                    current == '$' && index + 1 < source.length && source[index + 1] == '{' -> {
                        result.append("  ")
                        index += 2
                        templateBraceDepths.addLast(0)
                        returnStates.addLast(state)
                        state = 6
                    }
                    current == '\\' && index + 1 < source.length -> {
                        result.append(' ')
                        index += 1
                        val escaped = source[index]
                        result.append(if (escaped == '\n') '\n' else ' ')
                        index += 1
                    }
                    current == '\"' -> {
                        result.append(' ')
                        index += 1
                        state = returnStates.removeLast()
                    }
                    else -> {
                        result.append(if (current == '\n') '\n' else ' ')
                        index += 1
                    }
                }
                4 -> when {
                    current == '$' && index + 1 < source.length && source[index + 1] == '{' -> {
                        result.append("  ")
                        index += 2
                        templateBraceDepths.addLast(0)
                        returnStates.addLast(state)
                        state = 6
                    }
                    source.startsWith("\"\"\"", index) -> {
                        result.append("   ")
                        index += 3
                        state = returnStates.removeLast()
                    }
                    else -> {
                        result.append(if (current == '\n') '\n' else ' ')
                        index += 1
                    }
                }
                5 -> when {
                    current == '\\' && index + 1 < source.length -> {
                        result.append(' ')
                        index += 1
                        val escaped = source[index]
                        result.append(if (escaped == '\n') '\n' else ' ')
                        index += 1
                    }
                    current == '\'' -> {
                        result.append(' ')
                        index += 1
                        state = returnStates.removeLast()
                    }
                    else -> {
                        result.append(if (current == '\n') '\n' else ' ')
                        index += 1
                    }
                }
                6 -> when {
                    current == '{' -> {
                        val depth = templateBraceDepths.removeLast()
                        templateBraceDepths.addLast(depth + 1)
                        result.append(current)
                        index += 1
                    }
                    current == '}' -> {
                        val depth = templateBraceDepths.removeLast()
                        result.append(if (depth == 0) ' ' else current)
                        index += 1
                        if (depth == 0) {
                            state = returnStates.removeLast()
                        } else {
                            templateBraceDepths.addLast(depth - 1)
                        }
                    }
                    source.startsWith("//", index) -> {
                        result.append("  ")
                        index += 2
                        returnStates.addLast(state)
                        state = 1
                    }
                    source.startsWith("/*", index) -> {
                        result.append("  ")
                        index += 2
                        blockCommentDepth = 1
                        returnStates.addLast(state)
                        state = 2
                    }
                    source.startsWith("\"\"\"", index) -> {
                        result.append("   ")
                        index += 3
                        returnStates.addLast(state)
                        state = 4
                    }
                    current == '\"' -> {
                        result.append(' ')
                        index += 1
                        returnStates.addLast(state)
                        state = 3
                    }
                    current == '\'' -> {
                        result.append(' ')
                        index += 1
                        returnStates.addLast(state)
                        state = 5
                    }
                    else -> {
                        result.append(current)
                        index += 1
                    }
                }
                else -> error("Unknown Kotlin scanner state")
            }
        }
        return result.toString()
    }

    private fun hasRootPackageContent(code: String): Boolean {
        var fileAnnotationNesting = 0

        code.lineSequence().forEach { line ->
            var remaining = line.trim()
            if (remaining.isEmpty()) return@forEach
            if (fileAnnotationNesting > 0) {
                fileAnnotationNesting += remaining.count { it == '(' || it == '[' }
                fileAnnotationNesting -= remaining.count { it == ')' || it == ']' }
                return@forEach
            }
            while (remaining.isNotEmpty()) {
                if (remaining.startsWith("@file:")) {
                    fileAnnotationNesting = remaining.count { it == '(' || it == '[' }
                    fileAnnotationNesting -= remaining.count { it == ')' || it == ']' }
                    return@forEach
                }
                if (remaining.startsWith("package ") || remaining.startsWith("import ")) {
                    val separator = remaining.indexOf(';')
                    remaining = if (separator < 0) "" else remaining.substring(separator + 1).trimStart()
                    continue
                }
                return true
            }
        }
        return false
    }

    /** App-owned navigation entries are the only non-DI common composition boundary allowed Koin. */
    private fun isKoinCompositionRoot(source: ArchitectureSource, packageName: String): Boolean =
        packageName == "$sharedRootPackage.app" &&
            source.relativePath == navigationEntryKoinCompositionPath

    /**
     * Common AndroidX ViewModels are a deliberate presentation boundary for Nav3 entries. Keep the
     * exception file-scoped so screens cannot acquire arbitrary lifecycle/platform dependencies.
     */
    private fun isLifecyclePresentationEntry(source: ArchitectureSource, packageName: String): Boolean =
        packageName == "$sharedRootPackage.presentation.navigation" &&
            source.relativePath == navigationEntryViewModelsPath

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
            "\\b(?:Dao|DataSource|[A-Z][A-Za-z0-9_]*(?i:Dao|DataSource))\\b",
        )
        val koinReference = Regex("(?<![A-Za-z0-9_.])org\\.koin\\.")
        const val navigationEntryKoinCompositionPath =
            "src/commonMain/kotlin/com/denis/habitlab/shared/app/NavigationEntryKoinComposition.kt"
        const val navigationEntryViewModelsPath =
            "src/commonMain/kotlin/com/denis/habitlab/shared/presentation/navigation/NavigationEntryViewModels.kt"
    }
}
