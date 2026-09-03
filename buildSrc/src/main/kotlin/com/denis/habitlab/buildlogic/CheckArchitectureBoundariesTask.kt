package com.denis.habitlab.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class CheckArchitectureBoundariesTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val sourcesRoot = sourceDirectory.get().asFile
        val sources = sourcesRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { file ->
                ArchitectureSource(
                    relativePath = "src/commonMain/kotlin/" + file.relativeTo(sourcesRoot).invariantSeparatorsPath,
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
