import com.denis.habitlab.buildlogic.CheckDocumentationTask
import java.io.File

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.multiplatform.library) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room3) apply false
}

val checkDocumentation = tasks.register<CheckDocumentationTask>("checkDocumentation") {
    group = "verification"
    description = "Checks documentation routing, links, anchors, budgets, and canonical fact ownership."
    repositoryRoot.set(layout.projectDirectory)
    documentationFiles.from(
        fileTree(layout.projectDirectory) {
            include("AGENTS.md", "README.md", ".agents/**/*.md", "docs/**/*.md")
        },
    )
    factOwnersFile.set(layout.projectDirectory.file("docs/fact-owners.tsv"))
    budgetsFile.set(layout.projectDirectory.file("docs/document-budgets.tsv"))
}

val isNativeWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
val bashExecutable = if (isNativeWindows) {
    null
} else {
    System.getenv("PATH")
        ?.split(File.pathSeparatorChar)
        ?.asSequence()
        ?.map { entry -> File(entry.ifEmpty { "." }, "bash") }
        ?.firstOrNull { candidate -> candidate.isFile && candidate.canExecute() }
        ?.canonicalPath
}

val checkMaestroShell = tasks.register<Exec>("checkMaestroShell") {
    group = "verification"
    enabled = !isNativeWindows && bashExecutable != null
    description = when {
        isNativeWindows -> "Skipped on native Windows; the Maestro shell contract requires Bash."
        bashExecutable == null -> "Skipped because Bash is unavailable; Maestro shell coverage is not established."
        else -> "Checks Maestro navigation flows, Xcode preflight, and pinned runner selection."
    }
    if (!isNativeWindows && bashExecutable != null) {
        commandLine(bashExecutable, "ui-tests/maestro/tests/maestro-shell-test.sh")
    }
    workingDir(layout.projectDirectory)
}
