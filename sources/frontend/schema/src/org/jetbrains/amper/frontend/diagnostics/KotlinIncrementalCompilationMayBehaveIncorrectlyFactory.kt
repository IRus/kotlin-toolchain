/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.TraceableValue
import org.jetbrains.amper.frontend.api.asTraceableValue
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.schema.KotlinVersion
import org.jetbrains.amper.frontend.types.generated.*
import org.jetbrains.amper.problems.reporting.BuildProblem
import org.jetbrains.amper.problems.reporting.BuildProblemSource
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.DiagnosticId
import org.jetbrains.amper.problems.reporting.FileBuildProblemSource
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.MultipleLocationsBuildProblemSource
import org.jetbrains.amper.problems.reporting.ProblemReporter

object KotlinIncrementalCompilationMayBehaveIncorrectlyFactory : AomSingleModuleDiagnosticFactory {

    override fun analyze(module: AmperModule, problemReporter: ProblemReporter) {
        val reportedPlaces = mutableSetOf<Pair<Trace, Trace>>()
        module.fragments.forEach { fragment ->
            val compileIncrementally = fragment.settings.kotlin.compileIncrementally
            if (compileIncrementally && !fragment.settings.kotlin.version.supportsIncrementalCompilationInBTA()) {
                val alreadyReported = !reportedPlaces.add(Pair(
                    fragment.settings.kotlin.compileIncrementallyDelegate.trace,
                    fragment.settings.kotlin.versionDelegate.trace,
                ))
                if (!alreadyReported) {
                    problemReporter.reportMessage(
                        KotlinIncrementalCompilationMayBehaveIncorrectly(
                            incrementalCompilationTrace = fragment.settings.kotlin.compileIncrementallyDelegate.trace,
                            actualKotlinVersion = fragment.settings.kotlin.versionDelegate.asTraceableValue(),
                        )
                    )
                }
            }
        }
    }
}

class KotlinIncrementalCompilationMayBehaveIncorrectly(
    val incrementalCompilationTrace: Trace,
    val actualKotlinVersion: TraceableValue<KotlinVersion>,
) : BuildProblem {

    override val diagnosticId: DiagnosticId = FrontendDiagnosticId.KotlinIncrementalCompilationMayBehaveIncorrectly
    override val message = SchemaBundle.message("kotlin.incremental.compilation.may.behave.incorrectly", actualKotlinVersion.value.notation)
    override val level: Level = Level.Warning
    override val type: BuildProblemType = BuildProblemType.Generic
    override val source: BuildProblemSource = MultipleLocationsBuildProblemSource(
        sources = listOf(
            incrementalCompilationTrace.asBuildProblemSource(),
            actualKotlinVersion.asBuildProblemSource(),
        ).filterIsInstance<FileBuildProblemSource>(),
        groupingMessage = message,
    )
}
