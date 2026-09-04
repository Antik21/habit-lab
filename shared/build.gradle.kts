import com.denis.habitlab.buildlogic.CheckArchitectureBoundariesTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "com.denis.habitlab.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        androidResources {
            enable = true
        }
    }

    iosArm64()
    iosSimulatorArm64()
    // Apple Silicon runs the arm64 simulator; iosX64 is only configured on Intel hosts.
    // This avoids resolving an x64 simulator target during Apple Silicon checks while
    // retaining the target for Intel macOS contributors.
    if (HostManager.hostIsMac && HostManager.host.architecture == Architecture.X64) {
        iosX64()
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.lifecycle.viewmodel.navigation3)
            implementation(libs.orbit.core)
            implementation(libs.orbit.viewmodel)
            implementation(libs.orbit.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

val checkArchitectureBoundaries = tasks.register(
    "checkArchitectureBoundaries",
    CheckArchitectureBoundariesTask::class,
) {
    group = "verification"
    description = "Verifies the shared commonMain package dependency boundaries."
    val commonMainKotlinSources = layout.projectDirectory.dir("src/commonMain/kotlin")
    sourceDirectory.set(commonMainKotlinSources)
    sourcePathPrefix.set(
        commonMainKotlinSources.asFile
            .relativeTo(project.projectDir)
            .invariantSeparatorsPath,
    )
}

tasks.named("check") {
    dependsOn(checkArchitectureBoundaries)
}
