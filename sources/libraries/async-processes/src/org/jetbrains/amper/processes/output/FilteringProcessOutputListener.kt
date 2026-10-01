/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.processes.output

/**
 * A [ProcessOutputListener] that delegates all stdout/stderr lines to [delegate] if
 * they match the given filters.
 */
class FilteringProcessOutputListener(
    private val delegate: ProcessOutputListener,
    private val filterStdout: (line: String, pid: Long) -> Boolean,
    private val filterStderr: (line: String, pid: Long) -> Boolean = filterStdout,
) : ProcessOutputListener by delegate {
    override fun onStdoutLine(line: String, pid: Long) {
        if (filterStdout(line, pid)) delegate.onStdoutLine(line, pid)
    }

    override fun onStderrLine(line: String, pid: Long) {
        if (filterStderr(line, pid)) delegate.onStderrLine(line, pid)
    }
}