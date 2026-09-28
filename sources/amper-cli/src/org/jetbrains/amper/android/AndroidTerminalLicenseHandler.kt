/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.android

import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.rendering.Whitespace
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Panel
import com.github.ajalt.mordant.widgets.Text
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.amper.android.sdk.provisioning.AndroidLicense
import org.jetbrains.amper.android.sdk.provisioning.AndroidUnacceptedLicenseHandler
import org.jetbrains.amper.cli.terminal.promptBoolean
import org.jetbrains.amper.events.sink.OperationEventSink
import org.jetbrains.amper.events.sink.operationEventScope

internal class AndroidTerminalLicenseHandler(private val terminal: Terminal) : AndroidUnacceptedLicenseHandler {
    private val licenseWidgetMutex = Mutex()

    context(_: OperationEventSink)
    override suspend fun onUnacceptedLicense(packageDisplayName: String, license: AndroidLicense): Boolean {
        if (!terminal.terminalInfo.interactive) return false

        // To avoid displaying two licenses at the same time
        return licenseWidgetMutex.withLock {
            operationEventScope("Waiting for license acceptance", isInteractive = true) {
                terminal.println(Markdown("To use _\"${packageDisplayName}\"_, you need to accept the `${license.licenseId}` license."))
                val reviewLicenses = terminal.promptBoolean("Review the license?", default = true) ?: true
                if (!reviewLicenses) return false

                terminal.println(Panel(Text(license.text, whitespace = Whitespace.PRE_WRAP), expand = true))

                terminal.promptBoolean("Do you accept the license?", default = false) ?: false
            }
        }
    }
}