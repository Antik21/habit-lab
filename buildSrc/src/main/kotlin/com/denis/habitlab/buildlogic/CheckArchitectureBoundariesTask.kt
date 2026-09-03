package com.denis.habitlab.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class CheckArchitectureBoundariesTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty

    /**
     * The source directory's project-relative path. It is supplied during configuration so task
     * execution only consumes declared inputs and does not need a Project reference.
     */
    @get:Input
    abstract val sourcePathPrefix: Property<String>

    @TaskAction
    fun verify() {
        val sourcesRoot = sourceDirectory.get().asFile
        val pathPrefix = sourcePathPrefix.get().trimEnd('/')
        val sources = sourcesRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { file ->
                val sourceRelativePath = file.relativeTo(sourcesRoot).invariantSeparatorsPath
                ArchitectureSource(
                    relativePath = if (pathPrefix.isEmpty()) sourceRelativePath else "$pathPrefix/$sourceRelativePath",
                    content = file.readText(),
                )
            }
            .toList()
        val violations = ArchitectureBoundaryChecker().findViolations(sources)
        val report = violations.joinToString(separator = "\n")

        check(violations.isEmpty()) {
            "Architecture boundary violations:\n$report"
        }
    }
}
