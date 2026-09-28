/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation.compiler

import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.amper.ProcessRunner
import org.jetbrains.amper.compilation.KotlinArtifactsDownloader
import org.jetbrains.amper.compilation.ProblemReportingCompilerOutputListener
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.jdk.provisioning.Jdk
import org.jetbrains.amper.jdk.provisioning.majorVersion
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.amper.processes.ArgsMode
import org.jetbrains.amper.processes.LoggingProcessOutputListener
import org.jetbrains.amper.processes.ProcessResult
import org.jetbrains.amper.processes.output.ProcessOutputMode
import org.jetbrains.amper.processes.runJava
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Downloads the implementation of the embeddable Kotlin compiler in the given [version].
 *
 * The [version] should match the Kotlin version requested by the user, it is the version of the Kotlin compiler
 * that will be used behind the scenes.
 */
context(_: ProblemReporter)
internal suspend fun KotlinArtifactsDownloader.downloadKotlinCompiler(version: String, jdk: Jdk): KotlinCompiler =
    KotlinCompiler(
        compilerJars = downloadKotlinCompilerEmbeddable(version),
        kotlinVersion = ComparableVersion(version),
        jdk = jdk,
    )

/**
 * A type-safe wrapper around the Kotlin compiler CLI.
 */
internal class KotlinCompiler(
    private val compilerJars: List<Path>,
    private val kotlinVersion: ComparableVersion,
    private val jdk: Jdk,
) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(KotlinCompiler::class.java)

        /**
         * The first Kotlin version that brings the dedicated Kotlin/Wasm compiler (as a different main class).
         * Starting in this version, the `-Xwasm` compiler option is deprecated and shouldn't be used.
         */
        val KotlinVersionWithSeparateWasmCompiler = ComparableVersion("2.4.0")

        /**
         * The version of the Kotlin compiler in which the `-Xir-produce-klib-file` option became the default
         * (and produces a warning if specified explicitly).
         */
        val KotlinVersionWithPackedKlibByDefault = ComparableVersion("2.4.20")

        /**
         * The first compiler version without sun.misc.Unsafe usages.
         */
        val FirstKotlinVersionWithoutUnsafeUsages = ComparableVersion("2.4.0")
    }

    private val compilerWorkingDir = Path(".")

    context(processRunner: ProcessRunner, problemReporter: ProblemReporter)
    suspend fun compileMetadata(
        compilerArgs: List<String>,
        argsMode: ArgsMode.ArgFile,
        module: AmperModule,
    ): ProcessResult = compile(
        compilerArgs = compilerArgs,
        argsMode = argsMode,
        entryPoint = CompilerEntryPoint.Metadata,
        extraJvmArgs = buildList {
            if (jdk.majorVersion >= 24) {
                // The metadata compiler generates this warning on JDK 24+:
                // "sun.misc.Unsafe::invokeCleaner has been called by org.jetbrains.kotlin.cli.jvm.compiler.jarfs.FastJarFileSystemKt"
                // See KT-76799, where a workaround (JVM arg) is mentioned for the `kotlinc` start script.
                // Since we're launching Java by hand (not via the start script), we need to add it too.
                // The proper fix is for the compiler to remove Unsafe usages in FastJarFileSystemKt.
                add("--sun-misc-unsafe-memory-access=allow")
            }
        },
        module = module,
    )

    context(processRunner: ProcessRunner, problemReporter: ProblemReporter)
    suspend fun compileWeb(
        compilerArgs: List<String>,
        argsMode: ArgsMode.ArgFile,
        webPlatform: Platform,
        module: AmperModule,
    ): ProcessResult = compile(
        compilerArgs = compilerArgs,
        argsMode = argsMode,
        entryPoint = when (webPlatform) {
            Platform.JS -> CompilerEntryPoint.JavaScript
            Platform.WASM_JS,
            Platform.WASM_WASI -> if (kotlinVersion >= KotlinVersionWithSeparateWasmCompiler) {
                // The separate KotlinWasmCompiler main class was only introduced in 2.4.0 (see KT-56850)
                CompilerEntryPoint.WebAssembly
            } else {
                CompilerEntryPoint.JavaScript
            }
            else -> error("Unsupported platform for web compilation: ${webPlatform.name}")
        },
        module = module,
    )

    context(processRunner: ProcessRunner, problemReporter: ProblemReporter)
    private suspend fun compile(
        compilerArgs: List<String>,
        argsMode: ArgsMode.ArgFile,
        entryPoint: CompilerEntryPoint,
        extraJvmArgs: List<String> = [],
        module: AmperModule,
    ): ProcessResult = processRunner.runJava(
        jdk = jdk,
        workingDir = compilerWorkingDir,
        mainClass = entryPoint.mainClass,
        classpath = compilerJars,
        programArgs = compilerArgs,
        argsMode = argsMode,
        outputMode = ProcessOutputMode.listen(ProblemReportingCompilerOutputListener(
            reporter = problemReporter,
            moduleName = module.userReadableName,
            workingDir = compilerWorkingDir,
            logger = logger,
        )),
        jvmArgs = buildList {
            // The Kotlin compiler relies on Jansi (now Jline), which uses native calls:
            // "java.lang.System::load has been called by org.jetbrains.kotlin.org.fusesource.jansi.internal.JansiLoader"
            // We need to enable native access on JDK 24+ to avoid a warning (and in the future an error).
            // See KT-76111, which was fixed by adding the JVM flag to the compiler start script.
            // Since we're launching Java by hand (not via the start script), we need to add it too.
            // See also https://jline.org/docs/troubleshooting/#jdk-24-restricted-method-warning, which is the
            // recommendation from Jline themselves.
            if (jdk.majorVersion >= 24) {
                // We can't specify the Jansi module specifically when using a classpath (we would need module path),
                // so we go for `ALL-UNNAMED`.
                add("--enable-native-access=ALL-UNNAMED")
            }
            // Unsafe usages were removed in Kotlin 2.4.0
            if (jdk.majorVersion >= 24 && kotlinVersion < FirstKotlinVersionWithoutUnsafeUsages) {
                add("--sun-misc-unsafe-memory-access=allow")
            }
            addAll(extraJvmArgs)
        },
    )
}

private enum class CompilerEntryPoint(val mainClass: String) {
    Metadata(mainClass = "org.jetbrains.kotlin.cli.metadata.KotlinMetadataCompiler"),
    JavaScript(mainClass = "org.jetbrains.kotlin.cli.js.K2JSCompiler"),
    WebAssembly(mainClass = "org.jetbrains.kotlin.cli.js.KotlinWasmCompiler"),
}
