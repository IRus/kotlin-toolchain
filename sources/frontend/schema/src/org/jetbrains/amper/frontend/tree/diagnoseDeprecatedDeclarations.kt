/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.problems.reporting.ProblemReporter

/**
 * Automatically called in [org.jetbrains.amper.frontend.tree.reading.readTree].
 */
context(reporter: ProblemReporter)
fun diagnoseDeprecatedDeclarations(tree: TreeNode) {
    object : RecurringTreeVisitorUnit() {
        override fun visitMap(node: MappingNode) {
            for (keyValue in node.children) {
                keyValue.propertyDeclaration?.deprecated?.let { deprecatedInfo ->
                    reporter.reportMessage(
                        DeprecatedPropertyProblem(
                            info = deprecatedInfo,
                            property = keyValue,
                        )
                    )
                }
            }
            super.visitMap(node)
        }

        override fun visitScalar(node: ScalarNode) {
            if (node !is EnumNode) return
            node.declaration.getEntryByName(node.entryName)?.deprecated?.let { deprecatedInfo ->
                reporter.reportMessage(
                    DeprecatedEnumValueProblem(
                        info = deprecatedInfo,
                        value = node,
                    )
                )
            }
        }
    }.visit(tree)
}
