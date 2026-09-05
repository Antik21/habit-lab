package com.denis.habitlab.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class CheckDocumentationTask : DefaultTask() {
    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val documentationFiles: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val factOwnersFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val budgetsFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val root = repositoryRoot.get().asFile.canonicalFile
        val sources = documentationFiles.files
            .filter { it.isFile && it.extension.equals("md", ignoreCase = true) }
            .map { file ->
                DocumentationSource(
                    relativePath = file.canonicalFile.relativeTo(root).invariantSeparatorsPath,
                    content = file.readText(),
                )
            }
            .sortedBy(DocumentationSource::relativePath)
        val violations = DocumentationChecker().findViolations(
            repositoryRoot = root,
            sources = sources,
            factOwnersContent = factOwnersFile.get().asFile.readText(),
            budgetsContent = budgetsFile.get().asFile.readText(),
        )
        check(violations.isEmpty()) {
            "Documentation violations:\n${violations.joinToString("\n")}"
        }
    }
}
