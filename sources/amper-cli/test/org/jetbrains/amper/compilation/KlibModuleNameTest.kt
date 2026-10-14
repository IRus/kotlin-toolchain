/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation

import org.jetbrains.amper.engine.BuildTask
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.aomBuilder.readProjectModel
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.problems.reporting.NoopProblemReporter
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.wasm.WasmTarget
import org.jetbrains.amper.util.BuildType
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class KlibModuleNameTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `native klib uses publication identity without enabling publication or setting a version`() {
        val module = module("group: io.heapy.kotaml")
        val args = nativeArgs(module)
        assertContains(args, "-module-name=io.heapy.kotaml:kotaml")
        assertContains(args, "-Xshort-module-name=kotaml")
        assertEquals("kotaml.klib", KotlinCompilationType.LIBRARY.outputFilename(module, Platform.MACOS_ARM64, false))
    }

    @Test
    fun `native test klib has its own publication identity`() {
        val args = nativeArgs(module("group: io.heapy.kotaml"), isTest = true)
        assertContains(args, "-module-name=io.heapy.kotaml:kotaml_test")
        assertContains(args, "-Xshort-module-name=kotaml_test")
    }

    @Test
    fun `native identity uses explicit artifact id instead of directory name`() {
        val args = nativeArgs(module("group: io.heapy.kotaml\nartifactId: kotaml-core"))
        assertContains(args, "-module-name=io.heapy.kotaml:kotaml-core")
        assertContains(args, "-Xshort-module-name=kotaml")
    }

    @Test
    fun `modules without publishing group retain their native identity`() {
        val args = nativeArgs(module("artifactId: kotaml-core"))
        assertContains(args, "-module-name=kotaml")
    }

    @Test
    fun `native klib name override preserves the legacy identity and derives the test name`() {
        val module = module("group: io.heapy.kotaml", kotlin = "klibModuleName: kotaml")
        for (isTest in [false, true]) {
            val suffix = if (isTest) "_test" else ""
            val args = nativeArgs(module, isTest = isTest)
            assertEquals(["-module-name=kotaml$suffix"], args.filter { it.startsWith("-module-name=") })
            assertContains(args, "-Xshort-module-name=kotaml$suffix")
        }
    }

    @Test
    fun `native klib name override does not require publication coordinates`() {
        val args = nativeArgs(module("artifactId: kotaml-core", kotlin = "klibModuleName: io.heapy.kotaml:legacy"))
        assertContains(args, "-module-name=io.heapy.kotaml:legacy")
        assertContains(args, "-Xshort-module-name=kotaml")
    }

    @Test
    fun `native platform override takes precedence over the common klib name`() {
        val module = module(
            "group: io.heapy.kotaml",
            kotlin = "klibModuleName: io.heapy.kotaml:common",
            additionalSettings = """
                settings@native:
                  kotlin:
                    klibModuleName: io.heapy.kotaml:native
            """.trimIndent(),
        )
        assertContains(nativeArgs(module), "-module-name=io.heapy.kotaml:native")
        assertContains(nativeArgs(module, isTest = true), "-module-name=io.heapy.kotaml:native_test")
    }

    @Test
    fun `native metadata preserves the fragment suffix`() {
        val module = module("group: io.heapy.kotaml", platforms = "macosArm64, iosArm64")
        val fragment = module.fragments.first { !it.isTest && it.platforms.size > 1 }
        val args = nativeArgs(module, metadata = true)
        assertContains(args, "-module-name=io.heapy.kotaml:kotaml_${fragment.name}")
        assertContains(args, "-Xshort-module-name=kotaml_${fragment.name}")
    }

    @Test
    fun `native metadata derives its name from the override`() {
        val module = module(
            "group: io.heapy.kotaml",
            platforms = "macosArm64, iosArm64",
            additionalSettings = """
                settings@native:
                  kotlin:
                    klibModuleName: io.heapy.kotaml:legacy
            """.trimIndent(),
        )
        val fragment = module.fragments.first { !it.isTest && it.platforms.size > 1 }
        assertContains(nativeArgs(module, metadata = true), "-module-name=io.heapy.kotaml:legacy_${fragment.name}")
    }

    @Test
    fun `native executable retains its name and has no short module name argument`() {
        val module = module("group: io.heapy.kotaml", kotlin = "klibModuleName: io.heapy.kotaml:legacy")
        val args = nativeArgs(module, compilationType = KotlinCompilationType.BINARY)
        assertContains(args, "-module-name=kotaml")
        assertFalse(args.any { it.startsWith("-Xshort-module-name") })
        assertEquals("kotaml.kexe", KotlinCompilationType.BINARY.outputFilename(module, Platform.MACOS_ARM64, false))
    }

    @Test
    fun `iOS framework retains its name and has no short module name argument`() {
        val module = module("group: io.heapy.kotaml", kotlin = "klibModuleName: io.heapy.kotaml:legacy")
        val args = nativeArgs(module, compilationType = KotlinCompilationType.IOS_FRAMEWORK)
        assertContains(args, "-module-name=kotaml")
        assertFalse(args.any { it.startsWith("-Xshort-module-name") })
    }

    @Test
    fun `JVM compiler module name remains independent of publication coordinates`() {
        val module = module("group: io.heapy.kotaml\nartifactId: kotaml-core", platforms = "jvm")
        val fragment = module.leafFragments.first { !it.isTest }
        val args = kotlinJvmCompilerArgs(
            isMultiplatform = true,
            userSettings = fragment.serializableCompilationSettings(),
            classpath = [],
            compilerPlugins = [],
            jdkHome = tempDir,
            friendPaths = [],
            fragments = [],
            additionalSourceRoots = [],
            moduleName = module.kotlinModuleName(isTest = false),
        )
        assertContains(args, "-module-name=kotaml")
    }

    @ParameterizedTest
    @EnumSource(value = Platform::class, names = ["JS", "WASM_JS", "WASM_WASI"])
    fun `web identity is independent of the main and test output names`(platform: Platform) {
        val module = module("group: io.heapy.kotaml\nartifactId: kotaml-core", platforms = "js, wasmJs, wasmWasi")
        for (isTest in [false, true]) {
            val args = webArgs(module, platform, isTest)
            val suffix = if (isTest) "_test" else ""
            assertContains(args, "-Xir-module-name=io.heapy.kotaml:kotaml-core$suffix")
            assertEquals("kotaml$suffix", args[args.indexOf("-ir-output-name") + 1])
        }
    }

    @ParameterizedTest
    @EnumSource(value = Platform::class, names = ["JS", "WASM_JS", "WASM_WASI"])
    fun `web klib name override is inherited by tests`(platform: Platform) {
        val module = module(
            "group: io.heapy.kotaml",
            platforms = "js, wasmJs, wasmWasi",
            kotlin = "klibModuleName: io.heapy.kotaml:legacy",
        )
        for (isTest in [false, true]) {
            val args = webArgs(module, platform, isTest)
            val suffix = if (isTest) "_test" else ""
            assertContains(args, "-Xir-module-name=io.heapy.kotaml:legacy$suffix")
            assertEquals("kotaml$suffix", args[args.indexOf("-ir-output-name") + 1])
        }
    }

    @ParameterizedTest
    @EnumSource(value = Platform::class, names = ["JS", "WASM_JS", "WASM_WASI"])
    fun `web platform override takes precedence over the common klib name`(platform: Platform) {
        val module = module(
            "group: io.heapy.kotaml",
            platforms = "js, wasmJs, wasmWasi",
            kotlin = "klibModuleName: io.heapy.kotaml:common",
            additionalSettings = """
                settings@${platform.pretty}:
                  kotlin:
                    klibModuleName: io.heapy.kotaml:legacy
            """.trimIndent(),
        )
        assertContains(webArgs(module, platform), "-Xir-module-name=io.heapy.kotaml:legacy")
        assertContains(webArgs(module, platform, isTest = true), "-Xir-module-name=io.heapy.kotaml:legacy_test")
    }

    @ParameterizedTest
    @EnumSource(value = Platform::class, names = ["JS", "WASM_JS", "WASM_WASI"])
    fun `web explicit identity override is not duplicated`(platform: Platform) {
        val module = module("group: io.heapy.kotaml", platforms = "js, wasmJs, wasmWasi")
        val args = webArgs(module, platform, freeCompilerArgs = ["-Xir-module-name=custom-name"])
        assertEquals(["-Xir-module-name=custom-name"], args.filter { it.startsWith("-Xir-module-name") })
    }

    @ParameterizedTest
    @EnumSource(value = Platform::class, names = ["JS", "WASM_JS", "WASM_WASI"])
    fun `web linking keeps its output name and does not set klib identity`(platform: Platform) {
        val module = module("group: io.heapy.kotaml", platforms = "js, wasmJs, wasmWasi")
        val args = webArgs(module, platform, compilationType = KotlinCompilationType.BINARY)
        assertFalse(args.any { it.startsWith("-Xir-module-name") })
        assertEquals("kotaml", args[args.indexOf("-ir-output-name") + 1])
    }

    private fun webArgs(
        module: AmperModule,
        platform: Platform,
        isTest: Boolean = false,
        compilationType: KotlinCompilationType = KotlinCompilationType.LIBRARY,
        freeCompilerArgs: List<String> = [],
    ): List<String> {
        val settings = module.leafFragments.first { it.isTest == isTest && platform in it.platforms }
            .serializableKotlinSettings().copy(freeCompilerArgs = freeCompilerArgs)
        return if (platform == Platform.JS) {
            kotlinJsCompilerArgs(
                kotlinUserSettings = settings,
                compilerPlugins = [],
                libraryPaths = [],
                outputPath = tempDir / "output",
                friendPaths = [],
                fragments = [],
                sourceFiles = [],
                additionalSourceRoots = [],
                moduleName = module.kotlinModuleName(isTest),
                compilationType = compilationType,
                buildType = null,
                include = null,
                klibModuleName = module.klibModuleName(isTest, settings.klibModuleName),
            )
        } else {
            kotlinWasmCompilerArgs(
                wasmTarget = if (platform == Platform.WASM_JS) WasmTarget.JS else WasmTarget.WASI,
                kotlinUserSettings = settings,
                compilerPlugins = [],
                libraryPaths = [],
                outputPath = tempDir / "output",
                friendPaths = [],
                fragments = [],
                sourceFiles = [],
                additionalSourceRoots = [],
                moduleName = module.kotlinModuleName(isTest),
                compilationType = compilationType,
                buildType = null,
                include = null,
                klibModuleName = module.klibModuleName(isTest, settings.klibModuleName),
            )
        }
    }

    private fun module(
        publishing: String,
        platforms: String = "macosArm64",
        kotlin: String = "",
        additionalSettings: String = "",
    ): AmperModule {
        val root = (tempDir / "kotaml").createDirectories()
        (root / "module.yaml").writeText("""
            product:
              type: kmp/lib
              platforms: [$platforms]
            settings:
              publishing:
        """.trimIndent() + "\n" + publishing.prependIndent("    ") + "\n" +
                "  kotlin:\n" + kotlin.prependIndent("    ") + "\n" + additionalSettings + "\n")
        return context(NoopProblemReporter) {
            val project = AmperProjectContext.create(rootDir = root, buildDir = null)
                ?: error("Invalid project root: $root")
            project.readProjectModel(pluginData = [], mavenPluginXmls = []).modules.single()
        }
    }

    private fun nativeArgs(
        module: AmperModule,
        isTest: Boolean = false,
        compilationType: KotlinCompilationType = KotlinCompilationType.LIBRARY,
        metadata: Boolean = false,
    ): List<String> {
        val fragment = if (metadata) {
            module.fragments.first { it.isTest == isTest && it.platforms.size > 1 }
        } else {
            module.leafFragments.first { it.isTest == isTest }
        }
        return context(TestBuildTask(module, isTest)) {
            kotlinNativeCompilerArgs(
                buildType = BuildType.Debug,
                kotlinUserSettings = fragment.serializableKotlinSettings(),
                compilerPlugins = [],
                entryPoint = null,
                libraryPaths = [],
                exportedLibraryPaths = [],
                fragments = [fragment],
                sourceFiles = [],
                additionalSourceRoots = [],
                binaryOptions = emptyMap(),
                outputPath = tempDir / "output",
                compilationType = compilationType,
                include = null,
                fragmentPlatforms = fragment.platforms,
            )
        }
    }

    private class TestBuildTask(
        override val module: AmperModule,
        override val isTest: Boolean,
    ) : BuildTask {
        override val platform = Platform.MACOS_ARM64
        override val buildType = BuildType.Debug
        override val taskName = TaskName("klib-naming-test", "Compile")

        context(_: TaskGraphExecutionContext)
        override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult = error("Not executed")
    }
}
