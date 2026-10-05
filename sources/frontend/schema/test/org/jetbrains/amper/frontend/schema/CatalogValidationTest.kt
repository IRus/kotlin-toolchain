/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.catalogs.parseGradleVersionCatalog
import org.jetbrains.amper.frontend.catalogs.reportCatalogProblems
import org.jetbrains.amper.frontend.helpers.FrontendTestCaseBase
import org.jetbrains.amper.frontend.helpers.TestFrontendPathResolver
import org.jetbrains.amper.frontend.messages.PsiBuildProblemSource
import org.jetbrains.amper.problems.reporting.CollectingProblemReporter
import org.jetbrains.amper.problems.reporting.Level
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class CatalogValidationTest : FrontendTestCaseBase(Path("testResources") / "catalogs") {

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `normalized aliases cannot silently overwrite each other`(reverse: Boolean) {
        val entries = [
            "ktor-core = \"io.ktor:ktor-client-core:3.6.0\"",
            "ktor_core = \"io.ktor:ktor-server-core:3.6.0\"",
        ]
        val [catalog, reporter] = parse("[libraries]\n" + (if (reverse) entries.reversed() else entries).joinToString("\n"))

        assertNull(catalog.findInCatalog("libs.ktor.core"))
        val problems = reporter.problems.filter { it.diagnosticId.toString() == "CatalogAliasCollision" }
        assertEquals(2, problems.size)
        assertTrue(problems.all { "ktor-core" in it.message && "ktor_core" in it.message })
        assertEquals(entries.map { it.substringBefore(" =") }.toSet(), problems.map {
            assertIs<PsiBuildProblemSource>(it.source).psiElement.text
        }.toSet())
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "{ module = \"io.ktor:ktor-client-core\", version.ref = \"missing\" }",
        "{ group = \"io.ktor\", name = \"ktor-client-core\", version.ref = \"missing\" }",
    ])
    fun `undefined version refs report the reference instead of dropping the entry silently`(definition: String) {
        val [catalog, reporter] = parse("[libraries]\nktor-core = $definition\n")

        assertNull(catalog.findInCatalog("libs.ktor.core"))
        val problem = reporter.problems.single()
        assertEquals("UnresolvedCatalogVersion", problem.diagnosticId.toString())
        assertTrue("missing" in problem.message)
        assertEquals("\"missing\"", assertIs<PsiBuildProblemSource>(problem.source).psiElement.text)
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "\"io.ktor\"",
        "\"io.ktor::3.6.0\"",
        "\"io.ktor:ktor-core:\"",
        "\"io.ktor:ktor-core:3:classifier:extra\"",
        "\"io.ktor:ktor core:3.6.0\"",
        "{ module = \"io.ktor\", version = \"3.6.0\" }",
        "{ module = \"io.ktor:ktor-core:3.6.0\", version = \"3.6.0\" }",
        "{ group = \"io.ktor\", name = \"\", version = \"3.6.0\" }",
    ])
    fun `invalid coordinates are diagnosed before catalog substitution`(definition: String) {
        val [catalog, reporter] = parse("[libraries]\nktor-core = $definition\n")

        assertTrue(reporter.problems.isNotEmpty(), "Invalid coordinates must report a TOML error")
        assertNull(catalog.findInCatalog("libs.ktor.core"))
        val source = assertIs<PsiBuildProblemSource>(reporter.problems.first().source)
        assertEquals(buildDir / "libs.versions.toml", source.file)
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "\"io.ktor:ktor-core:3.6.0\"",
        "{ module = \"io.ktor:ktor-core\" }",
        "{ module = \"io.ktor:ktor-core\", version = \"3.6.0\" }",
    ])
    fun `valid coordinates including versionless modules remain available`(definition: String) {
        val [catalog, reporter] = parse("[libraries]\nktor-core = $definition\n")

        assertTrue(reporter.problems.isEmpty())
        assertNotNull(catalog.findInCatalog("libs.ktor.core"))
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "[libraries]\nktor-core = 123",
        "[libraries]\nktor-core = true",
        "[libraries]\nktor-core = []",
        "[libraries]\nktor-core = { module = true, version = \"3.6.0\" }",
        "[libraries]\nktor-core = { group = \"io.ktor\", name = 123, version = \"3.6.0\" }",
        "[libraries]\nktor-core = { module = \"io.ktor:ktor-core\", version = 123 }",
        "[libraries]\nktor-core = { module = \"io.ktor:ktor-core\", version.ref = 123 }",
        "[versions]\nktor = 123\n[libraries]\nktor-core = { module = \"io.ktor:ktor-core\", version.ref = \"ktor\" }",
        "[versions]\nunused = true\n[libraries]\nktor-core = \"io.ktor:ktor-core:3.6.0\"",
    ])
    fun `catalog fields must have their declared TOML types`(text: String) {
        val [_, reporter] = parse(text)

        assertTrue(reporter.problems.any { it.diagnosticId.toString() == "InvalidCatalogValueType" })
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "{ version = \"3.6.0\" }",
        "{ group = \"io.ktor\", version = \"3.6.0\" }",
        "{ name = \"ktor-core\", version = \"3.6.0\" }",
    ])
    fun `library tables require module or both group and name`(definition: String) {
        val [catalog, reporter] = parse("[libraries]\nktor-core = $definition\n")

        assertEquals("MissingCatalogModule", reporter.problems.single().diagnosticId.toString())
        assertNull(catalog.findInCatalog("libs.ktor.core"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["verison", "classifier", "unknown"])
    fun `unknown library fields are reported at the field key`(field: String) {
        val [catalog, reporter] = parse("[libraries]\nktor-core = { module = \"io.ktor:ktor-core\", $field = \"value\" }\n")

        val problem = reporter.problems.single()
        assertEquals("UnknownCatalogField", problem.diagnosticId.toString())
        assertEquals(field, assertIs<PsiBuildProblemSource>(problem.source).psiElement.text)
        assertNull(catalog.findInCatalog("libs.ktor.core"))
    }

    @Test
    fun `versionless group and name notation is supported`() {
        val [catalog, reporter] = parse("[libraries]\nktor-core = { group = \"io.ktor\", name = \"ktor-core\" }\n")

        assertTrue(reporter.problems.isEmpty())
        assertEquals("io.ktor:ktor-core", catalog.findInCatalog("libs.ktor.core")?.value)
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "[libraries]\nktor-core = { module = \"io.ktor:ktor-core\", version = { strictly = \"3.6.0\" } }",
        "[libraries]\nktor-core = { module = \"io.ktor:ktor-core\", version.prefer = \"3.6.0\" }",
        "[versions]\nktor = { require = \"3.6.0\" }\n[libraries]\nktor-core = { module = \"io.ktor:ktor-core\", version.ref = \"ktor\" }",
        "[versions]\nunused = { rejectAll = true }\n[libraries]\nktor-core = \"io.ktor:ktor-core:3.6.0\"",
    ])
    fun `unsupported version constraints produce an explicit diagnostic`(text: String) {
        val [_, reporter] = parse(text)

        assertTrue(reporter.problems.any { it.diagnosticId.toString() == "UnsupportedCatalogVersionConstraint" })
        assertTrue(reporter.problems.none { it.diagnosticId.toString() == "UnresolvedCatalogVersion" })
    }

    @Test
    fun `nested version reference table is not a version constraint`() {
        val [catalog, reporter] = parse("""
            [versions]
            ktor = "3.6.0"
            [libraries]
            ktor-core = { module = "io.ktor:ktor-core", version = { ref = "ktor" } }
        """.trimIndent())

        assertTrue(reporter.problems.isEmpty())
        assertEquals("io.ktor:ktor-core:3.6.0", catalog.findInCatalog("libs.ktor.core")?.value)
    }

    private fun parse(text: String): Pair<VersionCatalog, CollectingProblemReporter> {
        val catalogPath = buildDir / "libs.versions.toml"
        catalogPath.writeText(text)
        val resolver = TestFrontendPathResolver()
        val catalog = assertNotNull(resolver.parseGradleVersionCatalog(resolver.loadVirtualFile(catalogPath)))
        val reporter = CollectingProblemReporter()
        with(reporter) { catalog.reportCatalogProblems() }
        assertTrue(reporter.problems.all { it.level == Level.Error })
        return catalog to reporter
    }
}
