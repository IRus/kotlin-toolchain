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
