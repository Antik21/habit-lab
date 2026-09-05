package com.denis.habitlab.buildlogic

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.testfixtures.ProjectBuilder

class CheckDocumentationTaskTest {
    @Test
    fun `declares cache-relevant inputs and keeps repository root internal`() {
        val taskClass = CheckDocumentationTask::class.java

        assertNotNull(taskClass.getMethod("getDocumentationFiles").getAnnotation(InputFiles::class.java))
        assertNotNull(taskClass.getMethod("getFactOwnersFile").getAnnotation(InputFile::class.java))
        assertNotNull(taskClass.getMethod("getBudgetsFile").getAnnotation(InputFile::class.java))
        assertNotNull(taskClass.getMethod("getRepositoryRoot").getAnnotation(Internal::class.java))
        assertTrue(
            listOf("getDocumentationFiles", "getFactOwnersFile", "getBudgetsFile").all { getter ->
                taskClass.getMethod(getter).getAnnotation(PathSensitive::class.java)?.value ==
                    PathSensitivity.RELATIVE
            },
        )
    }

    @Test
    fun `ProjectBuilder task exposes declared input files and passes a valid fixture`() =
        withTaskFixture { fixture ->
            val task = fixture.task()
            val inputPaths = task.inputs.files.files.map { it.canonicalFile }.toSet()

            assertContains(inputPaths, fixture.root.resolve("AGENTS.md").canonicalFile)
            assertContains(inputPaths, fixture.root.resolve("README.md").canonicalFile)
            assertContains(inputPaths, fixture.root.resolve("docs/fact-owners.tsv").canonicalFile)
            assertContains(inputPaths, fixture.root.resolve("docs/document-budgets.tsv").canonicalFile)
            task.verify()
        }

    @Test
    fun `ProjectBuilder task fails with stable diagnostics for an invalid fixture`() =
        withTaskFixture { fixture ->
            fixture.root.resolve("README.md").writeText("[missing](docs/not-there.md)")

            val failure = assertFailsWith<IllegalStateException> { fixture.task().verify() }

            assertContains(failure.message.orEmpty(), "Documentation violations:")
            assertContains(
                failure.message.orEmpty(),
                "README.md:1: local link target does not exist: docs/not-there.md",
            )
        }

    private class TaskFixture private constructor(val root: java.io.File) : AutoCloseable {
        init {
            val sources = DocumentationChecker.REQUIRED_MARKDOWN_FILES.associateWith { "" }.toMutableMap()
            sources[DocumentationChecker.ROUTER_PATH] = buildString {
                appendLine("| key | request |")
                DocumentationChecker.REQUIRED_ROUTE_KEYS.sorted().forEach { key ->
                    appendLine("| $key | [doc](.agents/docs/00-routing.md) |")
                }
            }
            sources.forEach { (relativePath, content) ->
                root.toPath().resolve(relativePath).also { path ->
                    path.parent?.createDirectories()
                    path.writeText(content)
                }
            }
            root.resolve("docs/fact-owners.tsv").writeText("")
            root.resolve("docs/document-budgets.tsv").writeText(
                sources.keys.sorted().joinToString("\n") { "$it\t10000" },
            )
        }

        fun task(): CheckDocumentationTask {
            val project = ProjectBuilder.builder().withProjectDir(root).build()
            return project.tasks.register(
                "checkDocumentation",
                CheckDocumentationTask::class.java,
            ).get().apply {
                repositoryRoot.set(project.layout.projectDirectory)
                documentationFiles.from(
                    project.fileTree(project.layout.projectDirectory) {
                        include("AGENTS.md", "README.md", ".agents/**/*.md", "docs/**/*.md")
                    },
                )
                factOwnersFile.set(project.layout.projectDirectory.file("docs/fact-owners.tsv"))
                budgetsFile.set(project.layout.projectDirectory.file("docs/document-budgets.tsv"))
            }
        }

        override fun close() {
            root.deleteRecursively()
        }

        companion object {
            fun create(): TaskFixture = TaskFixture(
                Files.createTempDirectory("check-documentation-task-test").toFile(),
            )
        }
    }

    private companion object {
        inline fun <T> withTaskFixture(block: (TaskFixture) -> T): T = TaskFixture.create().use(block)
    }
}
