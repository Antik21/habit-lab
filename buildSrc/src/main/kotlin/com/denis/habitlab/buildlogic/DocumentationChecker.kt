package com.denis.habitlab.buildlogic

import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class DocumentationSource(
    val relativePath: String,
    val content: String,
)

class DocumentationChecker {
    fun findViolations(
        repositoryRoot: File,
        sources: List<DocumentationSource>,
        factOwnersContent: String,
        budgetsContent: String,
    ): List<String> {
        val violations = mutableListOf<String>()
        val sourceByPath = sources.associateBy { it.relativePath }
        violations += checkRequiredFiles(sourceByPath)
        violations += checkLinks(repositoryRoot.canonicalFile, sources, sourceByPath)
        violations += checkRoutes(sourceByPath[ROUTER_PATH])
        violations += checkBudgets(sources, budgetsContent)
        violations += checkFactOwners(sources, factOwnersContent)
        return violations.sortedWith(compareBy({ it.substringBefore(':') }, { lineNumber(it) }, { it }))
    }

    private fun checkRequiredFiles(sources: Map<String, DocumentationSource>): List<String> =
        REQUIRED_MARKDOWN_FILES.filterNot(sources::containsKey).map {
            "$ROUTER_PATH:1: required routed documentation file is missing: $it"
        }

    private fun checkLinks(
        root: File,
        sources: List<DocumentationSource>,
        sourceByPath: Map<String, DocumentationSource>,
    ): List<String> = sources.flatMap { source ->
        val visible = visibleLines(source.content).toList()
        val definitions = linkedMapOf<String, ReferenceDefinition>()
        val violations = mutableListOf<String>()

        visible.forEach { (lineNumber, line) ->
            parseReferenceDefinition(line)?.let { definition ->
                val previous = definitions.putIfAbsent(definition.normalizedLabel, definition)
                if (previous != null) {
                    violations += "${source.relativePath}:$lineNumber: duplicate reference definition: ${definition.label}"
                } else {
                    validateTarget(root, source, sourceByPath, lineNumber, definition.target)?.let(violations::add)
                }
            }
        }

        visible.forEach { (lineNumber, line) ->
            parseLinks(line).forEach { link ->
                when (link) {
                    is ParsedLink.Inline ->
                        validateTarget(root, source, sourceByPath, lineNumber, link.target)?.let(violations::add)
                    is ParsedLink.Reference -> if (link.normalizedLabel !in definitions) {
                        violations += "${source.relativePath}:$lineNumber: undefined reference link: ${link.label}"
                    }
                }
            }
        }
        violations
    }

    private fun validateTarget(
        root: File,
        source: DocumentationSource,
        sourceByPath: Map<String, DocumentationSource>,
        lineNumber: Int,
        rawTarget: String,
    ): String? {
        if (rawTarget.isEmpty() || isIgnoredExternalTarget(rawTarget)) return null
        val encodedPath = rawTarget.substringBefore('#')
        val encodedAnchor = rawTarget.substringAfter('#', missingDelimiterValue = "")
        val sourceFile = File(root, source.relativePath)
        val targetFile = if (encodedPath.isEmpty()) {
            sourceFile
        } else {
            File(sourceFile.parentFile, decodeUriComponent(encodedPath)).canonicalFile
        }
        if (!targetFile.toPath().startsWith(root.toPath())) {
            return "${source.relativePath}:$lineNumber: local link escapes repository root: $rawTarget"
        }
        if (!targetFile.exists()) {
            return "${source.relativePath}:$lineNumber: local link target does not exist: $rawTarget"
        }
        if (encodedAnchor.isNotEmpty() && targetFile.extension.equals("md", ignoreCase = true)) {
            val relativeTarget = targetFile.relativeTo(root).invariantSeparatorsPath
            val targetSource = sourceByPath[relativeTarget]
                ?: DocumentationSource(relativeTarget, targetFile.readText())
            if (decodeUriComponent(encodedAnchor) !in headingAnchors(targetSource.content)) {
                return "${source.relativePath}:$lineNumber: Markdown anchor does not exist: $rawTarget"
            }
        }
        return null
    }

    private fun checkRoutes(router: DocumentationSource?): List<String> {
        if (router == null) return emptyList()
        val definitions = visibleLines(router.content).mapNotNull { (_, line) ->
            parseReferenceDefinition(line)
        }.associateBy(ReferenceDefinition::normalizedLabel)
        val routes = visibleLines(router.content).mapNotNull { (lineNumber, line) ->
            val match = ROUTE_ROW.matchEntire(line) ?: return@mapNotNull null
            val key = match.groupValues[1]
            if (key == "key") return@mapNotNull null
            val targets = parseLinks(match.groupValues[2]).mapNotNull { link ->
                val target = when (link) {
                    is ParsedLink.Inline -> link.target
                    is ParsedLink.Reference -> definitions[link.normalizedLabel]?.target
                }
                target?.takeUnless(::isIgnoredExternalTarget)
                    ?.substringBefore('#')?.takeIf { it.endsWith(".md", ignoreCase = true) }
            }.distinct()
            Triple(lineNumber, key, targets)
        }.toList()
        val violations = mutableListOf<String>()
        if (routes.size < MINIMUM_ROUTE_COUNT) {
            violations += "$ROUTER_PATH:1: routing table must define at least $MINIMUM_ROUTE_COUNT route keys"
        }
        routes.groupBy { it.second }.filterValues { it.size > 1 }.forEach { (key, rows) ->
            violations += "$ROUTER_PATH:${rows.first().first}: duplicate route key: $key"
        }
        routes.forEach { (lineNumber, key, targets) ->
            if (targets.size !in 1..3) {
                violations += "$ROUTER_PATH:$lineNumber: route '$key' must link to 1-3 local Markdown documents"
            }
        }
        val routeKeys = routes.map { it.second }.toSet()
        REQUIRED_ROUTE_KEYS.filterNot(routeKeys::contains).forEach { key ->
            violations += "$ROUTER_PATH:1: required route key is missing: $key"
        }
        return violations
    }

    private fun checkBudgets(
        sources: List<DocumentationSource>,
        budgetsContent: String,
    ): List<String> {
        val (budgets, parseViolations) = parseRegistry(budgetsContent, BUDGETS_PATH, expectedColumns = 2)
        val violations = parseViolations.toMutableList()
        val budgetByPath = mutableMapOf<String, Int>()
        budgets.forEach { row ->
            val path = row.values[0]
            val budget = row.values[1].toIntOrNull()
            if (budget == null || budget <= 0) {
                violations += "$BUDGETS_PATH:${row.lineNumber}: word budget must be a positive integer"
            } else if (budgetByPath.put(path, budget) != null) {
                violations += "$BUDGETS_PATH:${row.lineNumber}: duplicate word budget path: $path"
            }
        }
        sources.forEach { source ->
            val budget = budgetByPath[source.relativePath]
            if (budget == null) {
                violations += "$BUDGETS_PATH:1: missing word budget for ${source.relativePath}"
            } else {
                val words = WORD.findAll(source.content).count()
                if (words > budget) {
                    violations += "${source.relativePath}:1: word budget exceeded: $words > $budget"
                }
            }
        }
        budgetByPath.keys.filterNot { path -> sources.any { it.relativePath == path } }.forEach { path ->
            violations += "$BUDGETS_PATH:1: word budget references a missing Markdown file: $path"
        }
        return violations
    }

    private fun checkFactOwners(
        sources: List<DocumentationSource>,
        factOwnersContent: String,
    ): List<String> {
        val (owners, parseViolations) = parseRegistry(factOwnersContent, FACT_OWNERS_PATH, expectedColumns = 3)
        val violations = parseViolations.toMutableList()
        val ownerByFact = mutableMapOf<String, RegistryRow>()
        val factBySignature = mutableMapOf<String, String>()
        owners.forEach { row ->
            val fact = row.values[0]
            val signature = row.values[2]
            if (ownerByFact.put(fact, row) != null) {
                violations += "$FACT_OWNERS_PATH:${row.lineNumber}: duplicate canonical fact id: $fact"
            }
            factBySignature.put(signature, fact)?.let { previousFact ->
                violations += "$FACT_OWNERS_PATH:${row.lineNumber}: canonical signature is shared by $previousFact and $fact"
            }
        }

        val markers = sources.flatMap { source ->
            visibleLines(source.content).flatMap { (lineNumber, line) ->
                FACT_MARKER.findAll(line).map { marker ->
                    FactMarker(marker.groupValues[1], source.relativePath, lineNumber)
                }
            }
        }
        ownerByFact.forEach { (fact, row) ->
            val expectedPath = row.values[1]
            val signature = row.values[2]
            val matches = markers.filter { it.fact == fact }
            when {
                matches.isEmpty() -> violations += "$FACT_OWNERS_PATH:${row.lineNumber}: canonical fact has no owner marker: $fact"
                matches.size > 1 -> violations += "${matches[1].path}:${matches[1].line}: canonical fact marker is duplicated: $fact"
                matches.single().path != expectedPath -> violations +=
                    "${matches.single().path}:${matches.single().line}: canonical fact '$fact' is owned by $expectedPath"
            }

            val ownerSource = sources.singleOrNull { it.relativePath == expectedPath }
            if (ownerSource == null) {
                violations += "$FACT_OWNERS_PATH:${row.lineNumber}: canonical owner Markdown file does not exist: $expectedPath"
            } else {
                val ownerOccurrences = exactOccurrenceLines(ownerSource.content, signature)
                if (ownerOccurrences.size != 1) {
                    violations += "$FACT_OWNERS_PATH:${row.lineNumber}: canonical signature for '$fact' must occur exactly once in $expectedPath (found ${ownerOccurrences.size})"
                }
            }
            sources.filter { it.relativePath != expectedPath }.forEach { other ->
                val duplicateLines = exactOccurrenceLines(other.content, signature)
                if (duplicateLines.isNotEmpty()) {
                    violations += "${other.relativePath}:${duplicateLines.first()}: canonical signature for '$fact' belongs only in $expectedPath (found ${duplicateLines.size})"
                }
            }
        }
        markers.filter { it.fact !in ownerByFact }.forEach { marker ->
            violations += "${marker.path}:${marker.line}: unregistered canonical fact marker: ${marker.fact}"
        }
        return violations
    }

    private fun parseRegistry(
        content: String,
        path: String,
        expectedColumns: Int,
    ): Pair<List<RegistryRow>, List<String>> {
        val rows = mutableListOf<RegistryRow>()
        val violations = mutableListOf<String>()
        content.lineSequence().forEachIndexed { index, rawLine ->
            val lineNumber = index + 1
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val values = rawLine.split('\t').map(String::trim)
            if (values.size != expectedColumns || values.any(String::isEmpty)) {
                violations += "$path:$lineNumber: expected $expectedColumns non-empty tab-separated columns"
            } else {
                rows += RegistryRow(lineNumber, values)
            }
        }
        return rows to violations
    }

    private fun parseReferenceDefinition(line: String): ReferenceDefinition? {
        val indent = line.indexOfFirst { it != ' ' }.let { if (it < 0) line.length else it }
        if (indent > 3 || indent >= line.length || line[indent] != '[') return null
        val labelEnd = findMatchingBracket(line, indent) ?: return null
        var index = labelEnd + 1
        while (index < line.length && line[index].isWhitespace()) index += 1
        if (index >= line.length || line[index] != ':') return null
        val label = unescapeMarkdown(line.substring(indent + 1, labelEnd))
        val target = parseDestinationAndTitle(line.substring(index + 1)) ?: return null
        return ReferenceDefinition(label, normalizeReferenceLabel(label), target)
    }

    private fun parseLinks(line: String): List<ParsedLink> {
        val links = mutableListOf<ParsedLink>()
        var index = 0
        while (index < line.length) {
            if (line[index] == '\\') {
                index += 2
                continue
            }
            val labelStart = when {
                line[index] == '[' -> index
                line[index] == '!' && index + 1 < line.length && line[index + 1] == '[' -> index + 1
                else -> {
                    index += 1
                    continue
                }
            }
            val labelEnd = findMatchingBracket(line, labelStart)
            if (labelEnd == null) {
                index = labelStart + 1
                continue
            }
            val label = unescapeMarkdown(line.substring(labelStart + 1, labelEnd))
            val suffixStart = labelEnd + 1
            when {
                suffixStart < line.length && line[suffixStart] == '(' -> {
                    val close = findMatchingParenthesis(line, suffixStart)
                    if (close != null) {
                        val content = line.substring(suffixStart + 1, close)
                        parseDestinationAndTitle(content)?.let { target ->
                            links += ParsedLink.Inline(target)
                        }
                        index = close + 1
                    } else {
                        index = suffixStart + 1
                    }
                }
                suffixStart < line.length && line[suffixStart] == '[' -> {
                    val referenceEnd = findMatchingBracket(line, suffixStart)
                    if (referenceEnd != null) {
                        val explicit = unescapeMarkdown(line.substring(suffixStart + 1, referenceEnd))
                        val referenceLabel = explicit.ifEmpty { label }
                        links += ParsedLink.Reference(referenceLabel, normalizeReferenceLabel(referenceLabel))
                        index = referenceEnd + 1
                    } else {
                        index = suffixStart + 1
                    }
                }
                else -> index = labelEnd + 1
            }
        }
        return links
    }

    private fun parseDestinationAndTitle(content: String): String? {
        var index = content.indexOfFirst { !it.isWhitespace() }
        if (index < 0) return ""
        val destination: String
        if (content[index] == '<') {
            val close = findUnescaped(content, '>', index + 1) ?: return null
            destination = unescapeMarkdown(content.substring(index + 1, close))
            index = close + 1
        } else {
            val start = index
            var depth = 0
            while (index < content.length) {
                when {
                    content[index] == '\\' -> index += 2
                    content[index] == '(' -> {
                        depth += 1
                        index += 1
                    }
                    content[index] == ')' -> {
                        if (depth == 0) return null
                        depth -= 1
                        index += 1
                    }
                    content[index].isWhitespace() && depth == 0 -> break
                    else -> index += 1
                }
            }
            if (depth != 0) return null
            destination = unescapeMarkdown(content.substring(start, index))
        }
        val remainder = content.substring(index).trim()
        if (remainder.isNotEmpty() && !isValidLinkTitle(remainder)) return null
        return destination
    }

    private fun isValidLinkTitle(value: String): Boolean {
        if (value.length < 2) return false
        val open = value.first()
        val close = when (open) {
            '"' -> '"'
            '\'' -> '\''
            '(' -> ')'
            else -> return false
        }
        if (value.last() != close) return false
        return findUnescaped(value, close, 1) == value.lastIndex
    }

    private fun findMatchingBracket(value: String, start: Int): Int? {
        var depth = 0
        var index = start
        while (index < value.length) {
            when {
                value[index] == '\\' -> index += 2
                value[index] == '[' -> {
                    depth += 1
                    index += 1
                }
                value[index] == ']' -> {
                    depth -= 1
                    if (depth == 0) return index
                    index += 1
                }
                else -> index += 1
            }
        }
        return null
    }

    private fun findMatchingParenthesis(value: String, start: Int): Int? {
        var depth = 0
        var index = start
        var angleDestination = false
        var quote: Char? = null
        while (index < value.length) {
            when {
                value[index] == '\\' -> index += 2
                quote != null && value[index] == quote -> {
                    quote = null
                    index += 1
                }
                quote != null -> index += 1
                angleDestination && value[index] == '>' -> {
                    angleDestination = false
                    index += 1
                }
                angleDestination -> index += 1
                value[index] == '<' -> {
                    angleDestination = true
                    index += 1
                }
                depth == 1 && index > start && value[index - 1].isWhitespace() &&
                    (value[index] == '"' || value[index] == '\'') -> {
                    quote = value[index]
                    index += 1
                }
                value[index] == '(' -> {
                    depth += 1
                    index += 1
                }
                value[index] == ')' -> {
                    depth -= 1
                    if (depth == 0) return index
                    index += 1
                }
                else -> index += 1
            }
        }
        return null
    }

    private fun findUnescaped(value: String, character: Char, start: Int): Int? {
        var index = start
        while (index < value.length) {
            if (value[index] == '\\') index += 2
            else if (value[index] == character) return index
            else index += 1
        }
        return null
    }

    private fun visibleLines(content: String): Sequence<Pair<Int, String>> = sequence {
        var fence: Fence? = null
        content.lineSequence().forEachIndexed { index, line ->
            val activeFence = fence
            if (activeFence == null) {
                val opener = parseFenceOpener(line)
                if (opener == null) yield(index + 1 to line) else fence = opener
            } else if (isFenceCloser(line, activeFence)) {
                fence = null
            }
        }
    }

    private fun parseFenceOpener(line: String): Fence? {
        val match = FENCE_OPENER.matchEntire(line) ?: return null
        val marker = match.groupValues[1]
        val info = match.groupValues[2]
        if (marker.first() == '`' && '`' in info) return null
        return Fence(marker.first(), marker.length)
    }

    private fun isFenceCloser(line: String, fence: Fence): Boolean {
        val match = FENCE_CLOSER.matchEntire(line) ?: return false
        val marker = match.groupValues[1]
        return marker.first() == fence.character && marker.length >= fence.length
    }

    private fun stripFencedCode(content: String): String =
        visibleLines(content).joinToString("\n") { it.second }

    private fun headingAnchors(content: String): Set<String> {
        val visible = visibleLines(content).toList()
        val headings = visible.mapIndexedNotNull { index, (lineNumber, line) ->
            ATX_HEADING.matchEntire(line)?.groupValues?.get(1)
                ?: SETEXT_UNDERLINE.matchEntire(line)?.let {
                    val previous = visible.getOrNull(index - 1)
                    previous?.second?.takeIf { candidate ->
                        previous.first == lineNumber - 1 &&
                            SETEXT_CONTENT.matchEntire(candidate) != null &&
                            ATX_HEADING.matchEntire(candidate) == null
                    }?.trim()
                }
        }
        val allocated = mutableSetOf<String>()
        return headings.map { heading ->
            val base = heading.lowercase()
                .replace(HTML_TAG, "")
                .replace(HEADING_PUNCTUATION, "")
                .trim()
                .replace(WHITESPACE, "-")
            var candidate = base
            var suffix = 1
            while (!allocated.add(candidate)) {
                candidate = "$base-$suffix"
                suffix += 1
            }
            candidate
        }.toSet()
    }

    private fun normalizeReferenceLabel(value: String): String =
        value.trim().replace(WHITESPACE, " ").lowercase()

    private fun unescapeMarkdown(value: String): String = value.replace(MARKDOWN_ESCAPE) { it.groupValues[1] }

    private fun decodeUriComponent(value: String): String = runCatching {
        URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8)
    }.getOrDefault(value)

    private fun isIgnoredExternalTarget(value: String): Boolean =
        value.substringBefore(':').lowercase() in IGNORED_SCHEMES && ':' in value

    private fun exactOccurrenceLines(content: String, exactText: String): List<Int> =
        visibleLines(content).flatMap { (lineNumber, line) ->
            sequence {
                var index = 0
                while (index <= line.length - exactText.length) {
                    val found = line.indexOf(exactText, startIndex = index)
                    if (found < 0) break
                    yield(lineNumber)
                    index = found + exactText.length
                }
            }
        }.toList()

    private fun lineNumber(violation: String): Int =
        violation.substringAfter(':', "0").substringBefore(':').toIntOrNull() ?: 0

    private sealed interface ParsedLink {
        data class Inline(val target: String) : ParsedLink
        data class Reference(val label: String, val normalizedLabel: String) : ParsedLink
    }

    private data class ReferenceDefinition(
        val label: String,
        val normalizedLabel: String,
        val target: String,
    )

    private data class RegistryRow(val lineNumber: Int, val values: List<String>)
    private data class FactMarker(val fact: String, val path: String, val line: Int)
    private data class Fence(val character: Char, val length: Int)

    companion object {
        const val ROUTER_PATH = "AGENTS.md"
        const val FACT_OWNERS_PATH = "docs/fact-owners.tsv"
        const val BUDGETS_PATH = "docs/document-budgets.tsv"
        const val MINIMUM_ROUTE_COUNT = 5
        private val DOC_NAMES = listOf(
            "routing", "stack-toolchain", "architecture-boundaries", "presentation-navigation",
            "data-offline-first", "platform-android", "platform-ios", "testing-verification",
            "libraries-licenses", "common-cases",
        )
        val REQUIRED_MARKDOWN_FILES = listOf(
            ROUTER_PATH,
            "README.md",
            ".agents/rules/compose.md",
            *(0..9).map { index -> ".agents/docs/${index.toString().padStart(2, '0')}-${DOC_NAMES[index]}.md" }.toTypedArray(),
            "docs/adr/README.md",
            "docs/adr/template.md",
        )
        val REQUIRED_ROUTE_KEYS = setOf(
            "screen", "route", "dialog", "repository-room", "android-adapter", "ios-adapter",
            "dependency-toolchain", "tests-verification",
        )
        private val IGNORED_SCHEMES = setOf("http", "https", "mailto")
        private val ROUTE_ROW = Regex("^\\|\\s*([a-z][a-z0-9-]*)\\s*\\|(.+)\\|\\s*$")
        private val FACT_MARKER = Regex("<!--\\s*fact-owner:\\s*([a-z0-9-]+)\\s*-->")
        private val FENCE_OPENER = Regex("^ {0,3}(`{3,}|~{3,})(.*)$")
        private val FENCE_CLOSER = Regex("^ {0,3}(`{3,}|~{3,})[ ]*$")
        private val ATX_HEADING = Regex("^ {0,3}#{1,6}(?:[ \\t]+|$)(.*?)(?:[ \\t]+#+[ \\t]*)?$")
        private val SETEXT_UNDERLINE = Regex("^ {0,3}(?:=+|-+)[ \\t]*$")
        private val SETEXT_CONTENT = Regex("^ {0,3}\\S.*$")
        private val HTML_TAG = Regex("<[^>]+>")
        private val HEADING_PUNCTUATION = Regex("[^\\p{L}\\p{N} _-]")
        private val WHITESPACE = Regex("\\s+")
        private val MARKDOWN_ESCAPE = Regex("\\\\([!\"#$%&'()*+,./:;<=>?@\\[\\]\\\\^_`{|}~-])")
        private val WORD = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}'’_-]*")
    }
}
