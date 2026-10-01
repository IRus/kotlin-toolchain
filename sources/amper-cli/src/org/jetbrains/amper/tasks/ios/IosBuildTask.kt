/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import com.github.ajalt.mordant.terminal.Terminal
import com.jetbrains.cidr.xcode.frameworks.buildSystem.BuildSettingNames
import kotlinx.serialization.json.Json
import org.jetbrains.amper.ProcessRunner
import org.jetbrains.amper.cli.context.ProjectCliContext
import org.jetbrains.amper.cli.telemetry.setAmperModule
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.engine.BuildTask
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.engine.requireSingleDependency
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.native.swiftpm.clangArch
import org.jetbrains.amper.tasks.native.swiftpm.xcodebuildPlatform
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.use
import org.jetbrains.amper.util.BuildType
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.pathString

class IosBuildTask(
    override val platform: Platform,
    override val module: AmperModule,
    override val buildType: BuildType,
    private val taskOutputPath: TaskOutputRoot,
    override val taskName: TaskName,
    private val userCacheRoot: AmperUserCacheRoot,
    private val processRunner: ProcessRunner,
    private val terminal: Terminal,
    private val buildSettingsResolution: XcodeBuildSettingsResolution,
) : BuildTask {
    init {
        require(platform.isDescendantOf(Platform.IOS)) { "Invalid iOS platform: $platform" }
    }

    override val isTest: Boolean
        get() = false

    context(executionContext: TaskGraphExecutionContext)
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val prebuildResult = dependenciesResult.requireSingleDependency<IosPreBuildTask.Result>()
        val settings = buildSettingsResolution.getResolver(buildType, dependenciesResult)

        val workingDir = taskOutputPath.path.createDirectories()
        val derivedDataPath = workingDir / "derivedData"
        val objRootPath = workingDir / "tmp"
        val symRootPath = workingDir / "bin"

        val xcodebuildArgs = buildList {
            this += XCRUN_EXECUTABLE
            this += "xcodebuild"
            this += "-project"; this += module.xcodeProjectPath.absolutePathString()
            this += "-scheme"; this += IosConventions.SCHEME_NAME
            this += "-destination"; this += "generic/platform=${platform.xcodebuildPlatform.destination}"
            this += "-configuration"; this += buildType.name
            this += "-derivedDataPath"; this += derivedDataPath.pathString
            this += "${BuildSettingNames.OBJROOT}=${objRootPath.pathString}"
            this += "${BuildSettingNames.SYMROOT}=${symRootPath.pathString}"
            this += "KOTLIN_CLI_WRAPPER_PATH=${ProjectCliContext.wrapperScriptPath.absolutePathString()}"
            if (platform.isIosSimulator) {
                // Constrain built architectures to avoid universal simulator build
                this +="${BuildSettingNames.ARCHS}=${platform.clangArch}"
            }
            val hasTeamId = !settings.developmentTeam.isNullOrBlank()
            val isSigningDisabled = settings.codeSigningAllowed == "NO"
            if (!platform.isIosSimulator && !hasTeamId && !isSigningDisabled) {
                logger.warn("`DEVELOPMENT_TEAM` build setting is not detected in the Xcode project. " +
                        "Adding `CODE_SIGNING_ALLOWED=NO` to disable signing. " +
                        "You can still sign the app manually later.")
                this += "CODE_SIGNING_ALLOWED=NO"
            }
            this += "build"
        }

        val result = runXcodebuildWithLogParsing(
            userCacheRoot = userCacheRoot,
            terminal = terminal,
            sink = executionContext.eventSink,
        ) { pipe ->
            spanBuilder("xcodebuild")
                .setAmperModule(module)
                .setListAttribute("args", xcodebuildArgs)
                .use { span ->
                    processRunner.runProcess(
                        workingDir = workingDir,
                        command = xcodebuildArgs,
                        span = span,
                        configureEnvironment = {
                            dependenciesResult.xcodeEnvironment().configureCommandEnvironment()
                            put(IosPreBuildTask.Result.ENV_JSON_NAME, Json.encodeToString(prebuildResult))
                        },
                        outputMode = pipe,
                    )
                }
        }
        if (result.exitCode.value != 0) {
            userReadableError("xcodebuild invocation failed with exit code ${result.exitCode}, check the log above.")
        }

        return Result(
            appPath = symRootPath / "${buildType.name}-${platform.xcodebuildPlatform.sdk}" / "${settings.productName}.app",
        )
    }

    class Result(
        val appPath: Path,
    ) : TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}

const val XCBEAUTIFY_VERSION = "3.2.1"