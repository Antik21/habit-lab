import com.denis.habitlab.buildlogic.CheckDocumentationTask

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
