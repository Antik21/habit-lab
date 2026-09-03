package com.denis.habitlab.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals

class ArchitectureBoundaryCheckerTest {
    @Test
    fun `reports a Kotlin source without a package declaration`() {
        assertEquals(
            listOf("NoPackage.kt: Kotlin source must declare a package"),
            checker.findViolations(
                listOf(ArchitectureSource(relativePath = "NoPackage.kt", content = "class NoPackage")),
            ),
        )
    }

    @Test
    fun `allows every declared layer dependency`() {
        val sources = listOf(
            layerSource("Core.kt", "core"),
            layerSource("Domain.kt", "domain", "core"),
            layerSource("Data.kt", "data", "core", "domain"),
            layerSource("Presentation.kt", "presentation", "core", "domain"),
            layerSource("App.kt", "app", "presentation"),
            layerSource("Di.kt", "di", "app", "core", "data", "domain", "presentation"),
        )

        assertEquals(emptyList(), checker.findViolations(sources))
    }

    @Test
    fun `reports every forbidden layer dependency`() {
        layers.forEach { layer ->
            layers
                .filterNot { dependency -> dependency == layer || dependency in allowedDependencies.getValue(layer) }
                .forEach { dependency ->
                    val relativePath = "${layer}DependsOn${dependency.replaceFirstChar(Char::uppercase)}.kt"
                    assertEquals(
                        listOf("$relativePath: $layer may not depend on shared.$dependency"),
                        checker.findViolations(
                            listOf(layerSource(relativePath, layer, dependency)),
                        ),
                        "$layer -> $dependency",
                    )
                }
        }
    }

    @Test
    fun `reports infrastructure in core domain and presentation`() {
        val sources = listOf(
            layerSource("CorePlatform.kt", "core", rawImports = listOf("platform.Foundation.NSObject")),
            layerSource("DomainJava.kt", "domain", rawImports = listOf("java.io.File")),
            layerSource("PresentationAndroid.kt", "presentation", rawImports = listOf("android.content.Context")),
        )

        assertEquals(
            listOf(
                "CorePlatform.kt: core must not use UI, DI, database, network, or platform SDKs",
                "DomainJava.kt: domain must remain pure common Kotlin",
                "PresentationAndroid.kt: presentation must not use native or infrastructure APIs",
            ),
            checker.findViolations(sources),
        )
    }

    @Test
    fun `reports Koin outside di and allows it in di`() {
        val outsideDi = layerSource(
            relativePath = "AppKoin.kt",
            layer = "app",
            rawImports = listOf("org.koin.dsl.module"),
        )
        val di = layerSource(
            relativePath = "DiKoin.kt",
            layer = "di",
            rawImports = listOf("org.koin.dsl.module"),
        )

        assertEquals(
            listOf("AppKoin.kt: org.koin references are only allowed in di"),
            checker.findViolations(listOf(outsideDi)),
        )
        assertEquals(emptyList(), checker.findViolations(listOf(di)))
    }

    @Test
    fun `reports a Koin import from the exact root package without reporting root content`() {
        val source = ArchitectureSource(
            relativePath = "RootKoin.kt",
            content = """
                package $sharedRoot

                import org.koin.dsl.module
            """.trimIndent(),
        )

        assertEquals(
            listOf("RootKoin.kt: org.koin references are only allowed in di"),
            checker.findViolations(listOf(source)),
        )
    }

    @Test
    fun `reports content in the shared root package but allows package-only files`() {
        val content = source(
            relativePath = "RootContent.kt",
            packageName = sharedRoot,
            body = "class RootContent",
        )
        val packageOnly = ArchitectureSource(
            relativePath = "RootPackageOnly.kt",
            content = """
                @file:Suppress(
                    "unused",
                )

                package $sharedRoot

                import kotlin.String
            """.trimIndent(),
        )

        assertEquals(
            listOf("RootContent.kt: shared root package is reserved for package-only files"),
            checker.findViolations(listOf(content)),
        )
        assertEquals(emptyList(), checker.findViolations(listOf(packageOnly)))
    }

    @Test
    fun `reports unqualified DAO and data source names after a wildcard import`() {
        val source = ArchitectureSource(
            relativePath = "PresentationPersistence.kt",
            content = """
                package $sharedRoot.presentation

                import com.example.persistence.*

                class PresentationPersistence(
                    private val dao: FooDao,
                    private val dataSource: FooDataSource,
                )
            """.trimIndent(),
        )

        assertEquals(
            listOf("PresentationPersistence.kt: presentation must not reference DAO or DataSource types"),
            checker.findViolations(listOf(source)),
        )
    }

    @Test
    fun `ignores forbidden-looking text in comments strings and character literals`() {
        val tripleQuote = "\"\"\""
        val source = ArchitectureSource(
            relativePath = "PresentationLiteralNoise.kt",
            content = """
                package $sharedRoot.presentation

                // import org.koin.dsl.module; val dao: FooDao = TODO()
                /* outer android.content.Context
                    /* nested platform.Foundation.NSObject and FooDataSource */
                */
                val normal = "io.ktor.client.HttpClient"
                val escaped = "quote: \\\" org.koin.dsl.module"
                val raw = $tripleQuote com.denis.habitlab.shared.data.repository.FooDao $tripleQuote
                val quote = '\''
                val slash = '/'
                val backslash = '\\\\'
            """.trimIndent(),
        )

        assertEquals(emptyList(), checker.findViolations(listOf(source)))
    }

    private fun layerSource(
        relativePath: String,
        layer: String,
        vararg dependencies: String,
        rawImports: List<String> = emptyList(),
    ): ArchitectureSource = source(
        relativePath = relativePath,
        packageName = "$sharedRoot.$layer",
        body = buildString {
            dependencies.forEach { dependency ->
                appendLine("import $sharedRoot.$dependency.Fixture")
            }
            rawImports.forEach { dependency ->
                appendLine("import $dependency")
            }
            append("class Fixture")
        },
    )

    private fun source(relativePath: String, packageName: String, body: String): ArchitectureSource =
        ArchitectureSource(
            relativePath = relativePath,
            content = """
                package $packageName

                $body
            """.trimIndent(),
        )

    private companion object {
        const val sharedRoot = "com.denis.habitlab.shared"
        val layers = listOf("app", "core", "data", "di", "domain", "presentation")
        val allowedDependencies = mapOf(
            "core" to emptySet<String>(),
            "domain" to setOf("core"),
            "data" to setOf("core", "domain"),
            "presentation" to setOf("core", "domain"),
            "app" to setOf("presentation"),
            "di" to setOf("app", "core", "data", "domain", "presentation"),
        )
        val checker = ArchitectureBoundaryChecker()
    }
}
