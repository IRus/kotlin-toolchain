/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.aomBuilder.readProjectModel
import org.jetbrains.amper.frontend.catalogs.parseGradleVersionCatalog
import org.jetbrains.amper.frontend.helpers.FrontendTestCaseBase
import org.jetbrains.amper.frontend.helpers.TestFrontendPathResolver
import org.jetbrains.amper.frontend.helpers.readProjectContextWithTestFrontendResolver
import org.jetbrains.amper.frontend.messages.PsiBuildProblemSource
import org.jetbrains.amper.problems.reporting.CollectingProblemReporter
import org.jetbrains.amper.problems.reporting.Level
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class GradleVersionCatalogTest : FrontendTestCaseBase(Path("testResources") / "catalogs") {

    @ParameterizedTest
    @ValueSource(strings = [
        "ktor.core = \"io.ktor:ktor-client-core:3.6.0\"",
        "ktor.core = { module = \"io.ktor:ktor-client-core\", version = \"3.6.0\" }",
        "ktor.core = { group = \"io.ktor\", name = \"ktor-client-core\", version.ref = \"ktor\" }",
        "ktor . core = \"io.ktor:ktor-client-core:3.6.0\"",
        "ktor.client.core = \"io.ktor:ktor-client-core:3.6.0\"",
        "\"ktor\".'core' = \"io.ktor:ktor-client-core:3.6.0\"",
    ])
    fun `dotted TOML keys are not library aliases`(definition: String) {
        val catalogPath = buildDir / "libs.versions.toml"
        catalogPath.writeText("[versions]\nktor = \"3.6.0\"\n[libraries]\n$definition\n")
        val resolver = TestFrontendPathResolver()
        val catalog = assertNotNull(resolver.parseGradleVersionCatalog(resolver.loadVirtualFile(catalogPath)))

        assertTrue(catalog.entries.isEmpty(), "A TOML dotted key must not become a library alias")
    }

    @Test
    fun `hyphens underscores and version refs still work`() {
        val catalogPath = buildDir / "libs.versions.toml"
        catalogPath.writeText("""
            [versions]
            ktor = "3.6.0"
            [libraries]
            ktor-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
            ktor_server = { group = "io.ktor", name = "ktor-server-core", version.ref = "ktor" }
        """.trimIndent())
        val resolver = TestFrontendPathResolver()
        val catalog = assertNotNull(resolver.parseGradleVersionCatalog(resolver.loadVirtualFile(catalogPath)))

        assertEquals("io.ktor:ktor-client-core:3.6.0", catalog.findInCatalog("libs.ktor.core")?.value)
        assertEquals("io.ktor:ktor-server-core:3.6.0", catalog.findInCatalog("libs.ktor.server")?.value)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `dotted key cannot replace a valid hyphen alias`(dottedKeyFirst: Boolean) {
        val validEntry = "ktor-core = \"io.ktor:ktor-client-core:3.6.0\""
        val invalidEntry = "ktor.core = \"io.ktor:ktor-client-core:0.0.0\""
        val entries = if (dottedKeyFirst) [invalidEntry, validEntry] else [validEntry, invalidEntry]
        val catalogPath = buildDir / "libs.versions.toml"
        catalogPath.writeText("[libraries]\n" + entries.joinToString("\n"))
        val resolver = TestFrontendPathResolver()
        val catalog = assertNotNull(resolver.parseGradleVersionCatalog(resolver.loadVirtualFile(catalogPath)))

        assertEquals("io.ktor:ktor-client-core:3.6.0", catalog.findInCatalog("libs.ktor.core")?.value)
    }

    @ParameterizedTest
    @ValueSource(strings = ["libs.versions.toml", "gradle/libs.versions.toml"])
    fun `unused dotted alias reports an error at the TOML key once per project`(catalogLocation: String) {
        (buildDir / "project.yaml").writeText("modules: [app, lib]\n")
        for (module in ["app", "lib"]) {
            val moduleDir = (buildDir / module).createDirectories()
            (moduleDir / "module.yaml").writeText("product: jvm/lib\n")
        }
        val catalogPath = buildDir / catalogLocation
        catalogPath.parent.createDirectories()
        catalogPath.writeText("[libraries]\nktor.core = \"io.ktor:ktor-client-core:3.6.0\"\n")
        val reporter = CollectingProblemReporter()
        with(reporter) {
            readProjectContextWithTestFrontendResolver(buildDir)
                .readProjectModel(pluginData = [], mavenPluginXmls = [])
        }

        val problem = reporter.problems.single()
        assertEquals(Level.Error, problem.level)
        assertEquals(
            "Dotted TOML keys are not supported as library aliases. Use 'ktor-core' instead of 'ktor.core'.",
            problem.message,
        )
        val source = assertIs<PsiBuildProblemSource>(problem.source)
        assertEquals("ktor.core", source.psiElement.text)
        assertEquals(catalogPath, source.file)
    }
}
