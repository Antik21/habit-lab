package com.denis.habitlab.buildlogic

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentationCheckerTest {
    @Test
    fun `accepts the required documentation set and one to three local documents per route`() =
        withFixture { fixture ->
            val sources = fixture.baselineSources()
            sources[DocumentationChecker.ROUTER_PATH] = router(
                "screen" to listOf(".agents/docs/00-routing.md"),
                "route" to listOf(".agents/docs/00-routing.md", ".agents/docs/01-stack-toolchain.md"),
                "dialog" to listOf(
                    ".agents/docs/00-routing.md",
                    ".agents/docs/01-stack-toolchain.md",
                    ".agents/docs/02-architecture-boundaries.md",
                ),
                *requiredRoutes.filterNot { it in setOf("screen", "route", "dialog") }
                    .map { it to listOf(".agents/docs/00-routing.md") }.toTypedArray(),
            )

            assertEquals(emptyList(), fixture.check(sources))
        }

    @Test
    fun `reports missing required files and missing duplicate and invalid route keys`() =
        withFixture { fixture ->
            val sources = fixture.baselineSources().apply {
                remove("README.md")
                this[DocumentationChecker.ROUTER_PATH] = router(
                    "route" to listOf(".agents/docs/00-routing.md"),
                    "dialog" to listOf(".agents/docs/00-routing.md"),
                    "repository-room" to listOf(".agents/docs/00-routing.md"),
                    "android-adapter" to listOf(".agents/docs/00-routing.md"),
                    "ios-adapter" to listOf(".agents/docs/00-routing.md"),
                    "dependency-toolchain" to listOf(".agents/docs/00-routing.md"),
                    "tests-verification" to listOf(".agents/docs/00-routing.md"),
                    "route" to listOf(
                        ".agents/docs/00-routing.md",
                        ".agents/docs/01-stack-toolchain.md",
                        ".agents/docs/02-architecture-boundaries.md",
                        ".agents/docs/03-presentation-navigation.md",
                    ),
                )
            }

            val violations = fixture.check(sources)

            assertContains(violations, "AGENTS.md:1: required routed documentation file is missing: README.md")
            assertContains(violations, "AGENTS.md:1: required route key is missing: screen")
            assertContains(violations, "AGENTS.md:2: duplicate route key: route")
            assertContains(violations, "AGENTS.md:9: route 'route' must link to 1-3 local Markdown documents")
        }

    @Test
    fun `reports too few route rows independently of required keys`() = withFixture { fixture ->
        val sources = fixture.baselineSources().apply {
            this[DocumentationChecker.ROUTER_PATH] = router(
                "screen" to listOf(".agents/docs/00-routing.md"),
                "route" to listOf(".agents/docs/00-routing.md"),
                "dialog" to listOf(".agents/docs/00-routing.md"),
                "repository-room" to listOf(".agents/docs/00-routing.md"),
            )
        }

        assertContains(
            fixture.check(sources),
            "AGENTS.md:1: routing table must define at least 5 route keys",
        )
    }

    @Test
    fun `parses escaped and balanced inline destinations angle destinations and titles`() =
        withFixture { fixture ->
            val sources = fixture.baselineSources().apply {
                this["README.md"] = """
                    [escaped](assets/guide\(v1\).txt)
                    [balanced](assets/guide(v2).txt)
                    [angle](<assets/with space.txt>)
                    [double](assets/double.txt "Double title")
                    [single](assets/single.txt 'Single title')
                    [parenthesized](assets/parenthesized.txt (Parenthesized title))
                """.trimIndent()
            }
            fixture.write("assets/guide(v1).txt")
            fixture.write("assets/guide(v2).txt")
            fixture.write("assets/with space.txt")
            fixture.write("assets/double.txt")
            fixture.write("assets/single.txt")
            fixture.write("assets/parenthesized.txt")

            assertEquals(emptyList(), fixture.check(sources))
        }

    @Test
    fun `supports full and collapsed references and normalizes labels`() = withFixture { fixture ->
        val sources = fixture.baselineSources().apply {
            this["README.md"] = """
                [full][Shared   Reference]
                [shared reference][]

                [ shared reference ]: assets/reference.txt "Reference title"
            """.trimIndent()
        }
        fixture.write("assets/reference.txt")

        assertEquals(emptyList(), fixture.check(sources))
    }

    @Test
    fun `reports undefined and duplicate reference definitions at stable lines`() =
        withFixture { fixture ->
            val sources = fixture.baselineSources().apply {
                this["README.md"] = """
                    [undefined][not defined]
                    [first]: assets/first.txt
                    [ FIRST ]: assets/second.txt
                """.trimIndent()
            }
            fixture.write("assets/first.txt")
            fixture.write("assets/second.txt")

            assertEquals(
                listOf(
                    "README.md:1: undefined reference link: not defined",
                    "README.md:3: duplicate reference definition:  FIRST ",
                ),
                fixture.check(sources),
            )
        }

    @Test
    fun `does not duplicate diagnostics for inline or reference images`() = withFixture { fixture ->
        val sources = fixture.baselineSources().apply {
            this["README.md"] = """
                ![inline](assets/missing-inline.png)
                ![reference][missing image]
            """.trimIndent()
        }

        assertEquals(
            listOf(
                "README.md:1: local link target does not exist: assets/missing-inline.png",
                "README.md:2: undefined reference link: missing image",
            ),
            fixture.check(sources),
        )
    }

    @Test
    fun `reports missing targets and lexical repository traversal`() = withFixture { fixture ->
        val sources = fixture.baselineSources().apply {
            this["README.md"] = """
                [missing](assets/missing.txt)
                [outside](../outside.txt)
            """.trimIndent()
        }

        assertEquals(
            listOf(
                "README.md:1: local link target does not exist: assets/missing.txt",
                "README.md:2: local link escapes repository root: ../outside.txt",
            ),
            fixture.check(sources),
        )
    }

    @Test
    fun `rejects a canonical symlink escape`() = withFixture { fixture ->
        val outside = Files.createTempFile("documentation-checker-outside", ".txt")
        try {
            Files.createSymbolicLink(fixture.root.resolve("escape.txt"), outside)
            val sources = fixture.baselineSources().apply {
                this["README.md"] = "[outside](escape.txt)"
            }

            assertEquals(
                listOf("README.md:1: local link escapes repository root: escape.txt"),
                fixture.check(sources),
            )
        } finally {
            Files.deleteIfExists(outside)
        }
    }

    @Test
    fun `validates same file and cross file ATX and Setext anchors`() = withFixture { fixture ->
        val sources = fixture.baselineSources().apply {
            this["README.md"] = """
                # Local Heading!
                [same](#local-heading)
                [atx](docs/headings.md#hello-world)
                [setext](docs/headings.md#setext-heading)
            """.trimIndent()
            this["docs/headings.md"] = """
                # Hello, *world*!

                Setext Heading
                --------------
            """.trimIndent()
        }

        assertEquals(emptyList(), fixture.check(sources))
    }

    @Test
    fun `preserves angle-bracket text in code spans while stripping actual HTML from anchors`() =
        withFixture { fixture ->
            val sources = fixture.baselineSources().apply {
                this["README.md"] = """
                    [code only](docs/code-span-headings.md#tag)
                    [mixed](docs/code-span-headings.md#prefix-tag)
                    [variable delimiter](docs/code-span-headings.md#double-tag-suffix)
                    [html](docs/code-span-headings.md#prefix-suffix)
                """.trimIndent()
                this["docs/code-span-headings.md"] = """
                    # `<Tag>`
                    # Prefix `<Tag>`
                    # Double ``<Tag>`` Suffix
                    # Prefix <Tag> Suffix
                """.trimIndent()
            }

            assertEquals(emptyList(), fixture.check(sources))
        }

    @Test
    fun `escaped backticks leave HTML tags outside code spans while even backslashes allow code spans`() =
        withFixture { fixture ->
            val sources = fixture.baselineSources().apply {
                this["README.md"] = """
                    [escaped valid](docs/escaped-backtick-headings.md#escaped-prefix-suffix)
                    [escaped invalid](docs/escaped-backtick-headings.md#escaped-prefix-tag-suffix)
                    [even backslashes](docs/escaped-backtick-headings.md#even-tag-suffix)
                """.trimIndent()
                this["docs/escaped-backtick-headings.md"] = """
                    # Escaped Prefix \`<Tag>\` Suffix
                    # Even \\`<Tag>` Suffix
                """.trimIndent()
            }

            assertEquals(
                listOf(
                    "README.md:2: Markdown anchor does not exist: " +
                        "docs/escaped-backtick-headings.md#escaped-prefix-tag-suffix",
                ),
                fixture.check(sources),
            )
        }

    @Test
    fun `backslash before a code span closer does not escape the closer`() = withFixture { fixture ->
        val sources = fixture.baselineSources().apply {
            this["README.md"] =
                "[escaped closer](docs/escaped-closer-heading.md#prefix-tag-suffix)"
            this["docs/escaped-closer-heading.md"] = "# Prefix `<Tag>\\` Suffix"
        }

        assertEquals(emptyList(), fixture.check(sources))
    }

    @Test
    fun `allocates the next free heading anchor across explicit suffix collisions`() =
        withFixture { fixture ->
            val sources = fixture.baselineSources().apply {
                this["README.md"] = """
                    [first](docs/collisions.md#a)
                    [explicit](docs/collisions.md#a-1)
                    [next free](docs/collisions.md#a-2)
                """.trimIndent()
                this["docs/collisions.md"] = """
                    # A
                    # A-1
                    # A
                """.trimIndent()
            }

            assertEquals(emptyList(), fixture.check(sources))
        }

    @Test
    fun `reports an absent Markdown anchor after percent decoding`() = withFixture { fixture ->
        val sources = fixture.baselineSources().apply {
            this["README.md"] = "[missing](docs/headings.md#not%20there)"
            this["docs/headings.md"] = "# Something Else"
        }

        assertEquals(
            listOf("README.md:1: Markdown anchor does not exist: docs/headings.md#not%20there"),
            fixture.check(sources),
        )
    }

    @Test
    fun `follows CommonMark fence indentation marker length type and info constraints`() =
        withFixture { fixture ->
            val sources = fixture.baselineSources().apply {
                this["README.md"] = listOf(
                    "   ```kotlin",
                    "[hidden](missing-1.txt)",
                    "   ```",
                    "~~~ `backticks allowed for tilde`",
                    "[hidden](missing-2.txt)",
                    "~~~",
                    "````",
                    "[hidden](missing-3.txt)",
                    "~~~~",
                    "[still hidden](missing-4.txt)",
                    "```",
                    "[still hidden](missing-5.txt)",
                    "````",
                    "    ```",
                    "[visible after indented pseudo fence](missing-visible.txt)",
                    "```bad`info",
                    "[visible after invalid backtick info](missing-info.txt)",
                ).joinToString("\n")
            }

            assertEquals(
                listOf(
                    "README.md:15: local link target does not exist: missing-visible.txt",
                    "README.md:17: local link target does not exist: missing-info.txt",
                ),
                fixture.check(sources),
            )
        }

    @Test
    fun `preserves literal plus decodes percent escapes and ignores external schemes case insensitively`() =
        withFixture { fixture ->
            val sources = fixture.baselineSources().apply {
                this["README.md"] = """
                    [plus](assets/a+b.txt)
                    [encoded](assets/with%20space.txt)
                    [http](HTTP://example.test/a)
                    [https](HtTpS://example.test/b)
                    [mail](MAILTO:test@example.test)
                """.trimIndent()
            }
            fixture.write("assets/a+b.txt")
            fixture.write("assets/with space.txt")

            assertEquals(emptyList(), fixture.check(sources))
        }

    @Test
    fun `counts fenced words and reports missing duplicate invalid and stale budgets`() =
        withFixture { fixture ->
            val sources = fixture.baselineSources().apply {
                this["README.md"] = """
                    ```text
                    one two three
                    ```
                """.trimIndent()
            }
            val budgets = defaultBudgets(sources)
                .lineSequence()
                .filterNot { it.startsWith("README.md\t") || it.startsWith(".agents/rules/compose.md\t") }
                .joinToString("\n") + "\n" + """
                AGENTS.md	10000
                .agents/rules/compose.md	zero
                docs/stale.md	10000
                README.md	2
                malformed
                """.trimIndent()

            val violations = fixture.check(sources, budgetsContent = budgets)

            assertContains(violations, "README.md:1: word budget exceeded: 4 > 2")
            assertContains(violations, "docs/document-budgets.tsv:1: missing word budget for .agents/rules/compose.md")
            assertTrue(violations.any { it.contains("duplicate word budget path: AGENTS.md") })
            assertTrue(violations.any { it.contains("word budget must be a positive integer") })
            assertTrue(violations.any { it.contains("word budget references a missing Markdown file: docs/stale.md") })
            assertTrue(violations.any { it.contains("expected 2 non-empty tab-separated columns") })
        }

    @Test
    fun `accepts a valid three column fact registry`() = withFixture { fixture ->
        val signature = "canonical documentation statement"
        val sources = fixture.baselineSources().apply {
            this["README.md"] = """
                <!-- fact-owner: documentation -->
                $signature
            """.trimIndent()
        }

        assertEquals(
            emptyList(),
            fixture.check(
                sources,
                factOwnersContent = "documentation\tREADME.md\t$signature",
            ),
        )
    }

    @Test
    fun `reports absent duplicated wrong and unregistered fact markers`() = withFixture { fixture ->
        val sources = fixture.baselineSources().apply {
            this["README.md"] = """
                <!-- fact-owner: duplicated -->
                duplicate-signature
                <!-- fact-owner: wrong-owner -->
                <!-- fact-owner: unknown -->
            """.trimIndent()
            this[".agents/docs/00-routing.md"] = """
                <!-- fact-owner: duplicated -->
                wrong-signature
            """.trimIndent()
            this[".agents/docs/01-stack-toolchain.md"] = "wrong-signature"
        }
        val facts = """
            absent	README.md	absent-signature
            duplicated	README.md	duplicate-signature
            wrong-owner	.agents/docs/00-routing.md	wrong-signature
        """.trimIndent()

        val violations = fixture.check(sources, factOwnersContent = facts)

        assertContains(violations, "docs/fact-owners.tsv:1: canonical fact has no owner marker: absent")
        assertContains(violations, ".agents/docs/00-routing.md:1: canonical fact marker is duplicated: duplicated")
        assertContains(
            violations,
            "README.md:3: canonical fact 'wrong-owner' is owned by .agents/docs/00-routing.md",
        )
        assertContains(violations, "README.md:4: unregistered canonical fact marker: unknown")
    }

    @Test
    fun `reports missing owners and absent duplicated and outside-owner signatures`() =
        withFixture { fixture ->
            val sources = fixture.baselineSources().apply {
                this["README.md"] = """
                    <!-- fact-owner: absent-signature -->
                    <!-- fact-owner: duplicate-signature -->
                    repeated repeated
                    <!-- fact-owner: outside-signature -->
                    owner-only
                    outside-only
                """.trimIndent()
                this[".agents/docs/00-routing.md"] = "outside-only"
            }
            val facts = """
                missing-owner	docs/not-present.md	missing-owner-signature
                absent-signature	README.md	never-present
                duplicate-signature	README.md	repeated
                outside-signature	README.md	outside-only
            """.trimIndent()

            val violations = fixture.check(sources, factOwnersContent = facts)

            assertContains(
                violations,
                "docs/fact-owners.tsv:1: canonical owner Markdown file does not exist: docs/not-present.md",
            )
            assertContains(
                violations,
                "docs/fact-owners.tsv:2: canonical signature for 'absent-signature' must occur exactly once in README.md (found 0)",
            )
            assertContains(
                violations,
                "docs/fact-owners.tsv:3: canonical signature for 'duplicate-signature' must occur exactly once in README.md (found 2)",
            )
            assertContains(
                violations,
                ".agents/docs/00-routing.md:1: canonical signature for 'outside-signature' belongs only in README.md (found 1)",
            )
        }

    @Test
    fun `reports malformed duplicate facts and shared signature ids in the registry`() =
        withFixture { fixture ->
            val sources = fixture.baselineSources().apply {
                this["README.md"] = """
                    <!-- fact-owner: fact-a -->
                    shared-signature
                """.trimIndent()
            }
            val facts = """
                fact-a	README.md	shared-signature
                fact-a	README.md	second-signature
                fact-b	README.md	shared-signature
                malformed	row
            """.trimIndent()

            val violations = fixture.check(sources, factOwnersContent = facts)

            assertContains(violations, "docs/fact-owners.tsv:2: duplicate canonical fact id: fact-a")
            assertContains(
                violations,
                "docs/fact-owners.tsv:3: canonical signature is shared by fact-a and fact-b",
            )
            assertContains(
                violations,
                "docs/fact-owners.tsv:4: expected 3 non-empty tab-separated columns",
            )
        }

    @Test
    fun `sorts diagnostics by path line and message independent of source order`() =
        withFixture { fixture ->
            val sources = fixture.baselineSources().apply {
                this["z.md"] = "[z](missing-z.txt)"
                this["a.md"] = """
                    first
                    [later](missing-later.txt)
                    [earlier](missing-earlier.txt)
                """.trimIndent()
            }.entries.reversed().associate { it.toPair() }

            assertEquals(
                listOf(
                    "a.md:2: local link target does not exist: missing-later.txt",
                    "a.md:3: local link target does not exist: missing-earlier.txt",
                    "z.md:1: local link target does not exist: missing-z.txt",
                ),
                fixture.check(sources),
            )
        }

    private class Fixture private constructor(val root: java.nio.file.Path) : AutoCloseable {
        fun baselineSources(): MutableMap<String, String> = linkedMapOf<String, String>().apply {
            DocumentationChecker.REQUIRED_MARKDOWN_FILES.forEach { path -> this[path] = "" }
            this[DocumentationChecker.ROUTER_PATH] = router(
                *requiredRoutes.map { it to listOf(".agents/docs/00-routing.md") }.toTypedArray(),
            )
        }

        fun write(relativePath: String, content: String = "") {
            root.resolve(relativePath).also { path ->
                path.parent?.createDirectories()
                path.writeText(content)
            }
        }

        fun check(
            sources: Map<String, String>,
            factOwnersContent: String = "",
            budgetsContent: String = defaultBudgets(sources),
        ): List<String> {
            sources.forEach(::write)
            return DocumentationChecker().findViolations(
                repositoryRoot = root.toFile(),
                sources = sources.map { (path, content) -> DocumentationSource(path, content) },
                factOwnersContent = factOwnersContent,
                budgetsContent = budgetsContent,
            )
        }

        override fun close() {
            root.toFile().deleteRecursively()
        }

        companion object {
            fun create(): Fixture = Fixture(Files.createTempDirectory("documentation-checker-test"))
        }
    }

    private companion object {
        val requiredRoutes = DocumentationChecker.REQUIRED_ROUTE_KEYS.sorted()

        fun router(vararg routes: Pair<String, List<String>>): String = buildString {
            appendLine("| key | request |")
            routes.forEach { (key, targets) ->
                append("| $key | ")
                append(targets.joinToString(" · ") { target -> "[doc]($target)" })
                appendLine(" |")
            }
        }.trimEnd()

        fun defaultBudgets(sources: Map<String, String>): String =
            sources.keys.sorted().joinToString("\n") { "$it\t10000" }

        inline fun <T> withFixture(block: (Fixture) -> T): T = Fixture.create().use(block)
    }
}
