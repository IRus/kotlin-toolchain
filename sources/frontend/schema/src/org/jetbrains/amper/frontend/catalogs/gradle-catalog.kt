/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.catalogs

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.childrenOfType
import org.jetbrains.amper.frontend.CompositeVersionCatalog
import org.jetbrains.amper.frontend.FileVersionCatalog
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.asTrace
import org.jetbrains.amper.frontend.diagnostics.FrontendDiagnosticId
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.problems.reporting.BuildProblem
import org.jetbrains.amper.problems.reporting.CollectingProblemReporter
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlKeyValueOwner
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable

private val TomlTable.headerText: String?
    get() = header.key?.keyText

private val TomlKeyValue.keyText: String
    get() = key.keyText

private val TomlKey.keyText: String
    get() = segments.joinToString(".") { it.text }

private val TomlKey.hasDots: Boolean
    get() = segments.size > 1 || segments.any { '.' in it.name.orEmpty() }

private fun TomlKeyValueOwner.getStringValueOrNull(key: String): String? {
    val keyValue = entries.find { it.keyText == key } ?: return null
    return keyValue.value?.takeIf { it is TomlLiteral }?.text?.removeSurrounding("\"")
}

private fun TomlFile.findTableOrNull(headerText: String): TomlTable? =
    childrenOfType<TomlTable>().firstOrNull { it.headerText == headerText }

private data class TomlLibraryDefinition(
    val libraryString: String,
    val element: PsiElement,
)

private class TomlCatalog(
    override val location: VirtualFile,
    private val libraries: Map<String, TomlLibraryDefinition>,
    val problems: List<BuildProblem>,
) : FileVersionCatalog {
    override val entries: Map<String, TraceableString>
        get() = libraries.map {
            val definition = it.value
            it.key to TraceableString(definition.libraryString, trace = definition.element.asTrace())
        }.toMap()
}

private class DottedCatalogAlias(
    override val element: TomlKey,
) : PsiBuildProblem(Level.Error, BuildProblemType.Generic) {
    override val diagnosticId = FrontendDiagnosticId.DottedCatalogAlias
    override val message: String
        get() = SchemaBundle.message(
            "catalog.library.alias.dotted",
            element.text,
            element.segments.joinToString("-") { it.name.orEmpty().replace('.', '-') },
        )
}

private class CatalogProblem(
    override val element: PsiElement,
    override val diagnosticId: FrontendDiagnosticId,
    messageKey: String,
    vararg parameters: Any,
) : PsiBuildProblem(Level.Error, BuildProblemType.Generic) {
    override val message: String = SchemaBundle.message(messageKey, *parameters)
}

/** Reports invalid aliases once when the project model is read, including unused entries. */
context(problemReporter: ProblemReporter)
internal fun VersionCatalog?.reportCatalogProblems() {
    when (this) {
        is TomlCatalog -> problems.forEach(problemReporter::reportMessage)
        is CompositeVersionCatalog -> catalogs.forEach { it.reportCatalogProblems() }
        else -> Unit
    }
}

/**
 * A gradle compliant version catalog, that supports only:
 * 1. `[versions]` and `[libraries]` sections, no `[plugins]` or `[bundles]`
 * 2. versions or version refs, no version constraints
 */
internal fun FrontendPathResolver.parseGradleVersionCatalog(
    catalogFile: VirtualFile
): VersionCatalog? {
    val psiFile = toPsiFile(catalogFile) as? TomlFile ?: return null
    val librariesTable = psiFile.findTableOrNull("libraries") ?: return null
    val reporter = CollectingProblemReporter()
    val libraries = with(reporter) { librariesTable.parseCatalogLibraries() }
    return TomlCatalog(
        location = catalogFile,
        libraries = libraries,
        problems = librariesTable.entries.map { it.key }.filter { it.hasDots }.map(::DottedCatalogAlias) + reporter.problems,
    )
}

/**
 * Get `[libraries]` table, parse it and normalize libraries aliases
 * to match "libs.my.lib" format.
 */
context(problemReporter: ProblemReporter)
private fun TomlTable.parseCatalogLibraries(): Map<String, TomlLibraryDefinition> {
    fun String.normalizeLibraryKey() = "libs." + replace("-", ".").replace("_", ".")

    val librariesAliases = entries.filterNot { it.key.hasDots }
    val aliasesByKey = librariesAliases.groupBy { it.keyText.normalizeLibraryKey() }
    for ([key, aliases] in aliasesByKey) {
        if (aliases.size > 1) {
            for (alias in aliases) {
                problemReporter.reportMessage(CatalogProblem(
                    alias.key,
                    FrontendDiagnosticId.CatalogAliasCollision,
                    "catalog.library.alias.collision",
                    aliases.joinToString(", ") { it.key.text },
                    key,
                ))
            }
        }
    }
    return buildMap {
        for (entry in librariesAliases) {
            val aliasKey = entry.keyText.normalizeLibraryKey()
            if (aliasesByKey.getValue(aliasKey).size > 1) continue

            // my-lib = "com.mycompany:mylib:1.4"
            val value = getInlineNotation(entry) ?: continue
            put(aliasKey, TomlLibraryDefinition(value, entry))
        }
    }
}

context(problemReporter: ProblemReporter)
private fun getInlineNotation(catalogEntry: TomlKeyValue): String? {
    return when (val libraryValue = catalogEntry.value) {
        is TomlLiteral -> libraryValue.text.removeSurrounding("\"")
        is TomlInlineTable -> {
            val version = libraryValue.getStringValueOrNull("version")
            val versionRef = libraryValue.getStringValueOrNull("version.ref")

            val module = libraryValue.getStringValueOrNull("module")
            val group = libraryValue.getStringValueOrNull("group")
            val name = libraryValue.getStringValueOrNull("name")

            val finalModuleName = when {
                module != null -> module
                group != null && name != null -> "$group:$name"
                else -> null
            } ?: return null

            // The version might come from BOM (currently supported only with Gradle)
            if (version == null && versionRef == null && module != null) return finalModuleName

            val finalVersion = when {
                version != null -> version
                versionRef != null -> {
                    val file = catalogEntry.containingFile as TomlFile
                    val versions = file.findTableOrNull("versions")
                    val resolvedVersion = versions?.getStringValueOrNull(versionRef)
                    if (resolvedVersion == null) {
                        problemReporter.reportMessage(CatalogProblem(
                            libraryValue.entries.first { it.keyText == "version.ref" }.value ?: catalogEntry,
                            FrontendDiagnosticId.UnresolvedCatalogVersion,
                            "catalog.version.ref.unresolved",
                            versionRef,
                        ))
                    }
                    resolvedVersion
                }

                else -> null
            } ?: return null

            "$finalModuleName:$finalVersion"
        }

        else -> null
    }
}
