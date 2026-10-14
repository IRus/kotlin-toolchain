/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test.core

import org.jetbrains.amper.cli.test.CliTestBase
import org.jetbrains.amper.cli.test.utils.getTaskOutputPath
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.jetbrains.amper.test.AmperCliResult
import org.jetbrains.amper.test.MacOnly
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import java.util.Properties
import java.util.zip.ZipFile
import kotlin.io.path.appendText
import kotlin.io.path.div
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

@Tag("cli-test-group-core")
class KlibModuleNameTest : CliTestBase() {
    @Test
    @MacOnly
    fun `native main and test klibs have distinct publication identities`() = runSlowTest {
        val project = testProject("klib-publication-identity")
        val result = runCli(project, "test", "--platform=macosArm64")
        result.assertIdentities("MacosArm64", "io.heapy.kotaml:kotaml", buildTypeSuffix = "Debug")
    }

    @ParameterizedTest
    @ValueSource(strings = ["kotaml", "io.heapy.kotaml:legacy"])
    @MacOnly
    fun `native klib name override keeps test linking working`(name: String) = runSlowTest {
        val project = testProject("klib-publication-identity")
        project.setKlibModuleName("native", name)
        val result = runCli(project, "test", "--platform=macosArm64")
        result.assertIdentities("MacosArm64", name, buildTypeSuffix = "Debug")
    }

    @Test
    @MacOnly
    fun `changing klib identity invalidates native klib compilation`() = runSlowTest {
        val project = testProject("klib-publication-identity")
        val output = tempRoot / "build"
        val initial = runCli(project, "test", "--platform=macosArm64", buildOutputRoot = output)
        initial.assertIdentities("MacosArm64", "io.heapy.kotaml:kotaml", buildTypeSuffix = "Debug")
        // Changing coordinates must invalidate compilation even though Kotlin settings and sources are unchanged.
        val moduleFile = project / "kotaml" / "module.yaml"
        moduleFile.writeText(moduleFile.readText().replace("group: io.heapy.kotaml", "group: io.heapy.kotaml.changed"))
        val rebuilt = runCli(project, "test", "--platform=macosArm64", buildOutputRoot = output)
        rebuilt.assertIdentities("MacosArm64", "io.heapy.kotaml.changed:kotaml", buildTypeSuffix = "Debug")

        project.setKlibModuleName("native", "kotaml")
        val overridden = runCli(project, "test", "--platform=macosArm64", buildOutputRoot = output)
        overridden.assertIdentities("MacosArm64", "kotaml", buildTypeSuffix = "Debug")
    }

    @ParameterizedTest
    @ValueSource(strings = ["js", "wasmJs", "wasmWasi"])
    fun `web klibs use publication identity and invalidate compilation when overridden`(platform: String) = runSlowTest {
        val project = testProject("klib-publication-identity")
        val output = tempRoot / "build"
        val taskPlatform = platform.replaceFirstChar { it.uppercaseChar() }
        val initial = runCli(project, "build", "--platform=$platform", buildOutputRoot = output)
        initial.assertIdentities(taskPlatform, "io.heapy.kotaml:kotaml")

        project.setKlibModuleName(platform, "io.heapy.kotaml:legacy")
        val overridden = runCli(project, "build", "--platform=$platform", buildOutputRoot = output)
        overridden.assertIdentities(taskPlatform, "io.heapy.kotaml:legacy")
    }

    private fun Path.setKlibModuleName(platform: String, name: String) {
        (this / "kotaml" / "module.yaml").appendText("\nsettings@$platform:\n  kotlin:\n    klibModuleName: $name\n")
    }

    private fun AmperCliResult.assertIdentities(platform: String, moduleName: String, buildTypeSuffix: String = "") {
        for (isTest in [false, true]) {
            val testTaskSuffix = if (isTest) "Test" else ""
            val testModuleSuffix = if (isTest) "_test" else ""
            val taskName = ":kotaml:compile$platform$testTaskSuffix$buildTypeSuffix"
            val artifact = getTaskOutputPath(taskName) / "kotaml$testModuleSuffix.klib"
            assertEquals("$moduleName$testModuleSuffix", artifact.klibUniqueName(), artifact.toString())
        }
    }

    private fun Path.klibUniqueName(): String = ZipFile(toFile()).use { zip ->
        val entry = zip.getEntry("default/manifest") ?: error("No klib manifest in $this")
        val manifest = zip.getInputStream(entry).use { Properties().apply { load(it) } }
        manifest.getProperty("unique_name") ?: error("No unique_name in $this")
    }
}
