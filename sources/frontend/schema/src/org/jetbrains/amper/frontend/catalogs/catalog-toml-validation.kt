/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.catalogs

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.childrenOfType
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.diagnostics.FrontendDiagnosticId
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind
import org.toml.lang.psi.TomlKeyValueOwner
import org.toml.lang.psi.TomlTable

private class InvalidCatalogToml(
    override val element: PsiElement,
    description: String,
) : PsiBuildProblem(Level.Error, BuildProblemType.Generic) {
    override val diagnosticId = FrontendDiagnosticId.InvalidCatalogToml
    override val message: String = SchemaBundle.message("catalog.toml.invalid", description)
}

context(reporter: ProblemReporter)
internal fun validateCatalogToml(file: TomlFile) {
    for (error in PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java)) {
        reporter.reportMessage(InvalidCatalogToml(error, error.errorDescription))
    }
    for (literal in PsiTreeUtil.collectElementsOfType(file, TomlLiteral::class.java)) {
        val string = literal.kind as? TomlLiteralKind.String ?: continue
        if (string.offsets.closeDelim == null) {
            reporter.reportMessage(InvalidCatalogToml(literal, "Unterminated string"))
        }
    }
    val tables = file.childrenOfType<TomlTable>()
    val declaredTables = mutableSetOf<List<String?>>()
    for (table in tables) {
        val key = table.header.key ?: continue
        if (!declaredTables.add(key.segments.map { it.name })) {
            reporter.reportMessage(InvalidCatalogToml(key, "Table ${key.text} is already defined"))
        }
    }
    validateKeys(file.childrenOfType<TomlKeyValue>())
    for (owner in PsiTreeUtil.collectElementsOfType(file, TomlKeyValueOwner::class.java)) {
        validateKeys(owner.entries)
    }
}

context(reporter: ProblemReporter)
private fun validateKeys(entries: List<TomlKeyValue>) {
    val declared = mutableMapOf<List<String?>, TomlKey>()
    val descendants = mutableMapOf<List<String?>, TomlKey>()
    for (entry in entries) {
        val path = entry.key.segments.map { it.name }
        val existing = declared[path] ?: descendants[path] ?:
                (1 until path.size).firstNotNullOfOrNull { declared[path.take(it)] }
        if (existing != null) {
            reporter.reportMessage(InvalidCatalogToml(entry.key, "Key ${entry.key.text} conflicts with ${existing.text}"))
        }
        declared[path] = entry.key
        for (length in 1 until path.size) descendants.putIfAbsent(path.take(length), entry.key)
    }
}
