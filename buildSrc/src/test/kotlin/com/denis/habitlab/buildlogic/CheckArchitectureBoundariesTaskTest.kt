package com.denis.habitlab.buildlogic

import java.nio.file.Files
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class CheckArchitectureBoundariesTaskTest {
    @Test
    fun `reports violations with the configured source path prefix`() {
        val projectDirectory = Files.createTempDirectory("architecture-boundaries-test").toFile()
        try {
            val sourceRoot = projectDirectory.resolve("src/commonMain/kotlin")
            val sourceFile = sourceRoot.resolve("outside/ExternalPackage.kt")
            sourceFile.parentFile.mkdirs()
            sourceFile.writeText("package com.example.outside")

            val project = ProjectBuilder.builder()
                .withProjectDir(projectDirectory)
                .build()
            val task = project.tasks.register(
                "checkArchitectureBoundaries",
                CheckArchitectureBoundariesTask::class.java,
            ).get()
            task.sourceDirectory.set(project.layout.projectDirectory.dir("src/commonMain/kotlin"))
            task.sourcePathPrefix.set("src/commonMain/kotlin/")

            val failure = assertFailsWith<IllegalStateException> { task.verify() }

            assertContains(
                failure.message.orEmpty(),
                "src/commonMain/kotlin/outside/ExternalPackage.kt: package must be under com.denis.habitlab.shared",
            )
        } finally {
            projectDirectory.deleteRecursively()
        }
    }
}
