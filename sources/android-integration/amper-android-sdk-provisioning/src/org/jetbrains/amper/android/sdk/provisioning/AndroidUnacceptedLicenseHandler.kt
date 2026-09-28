/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.android.sdk.provisioning

import org.jetbrains.amper.events.sink.OperationEventSink

/**
 * Handler that can be passed to the construction of [AndroidSdkProvider] to support interactive handling of
 * unaccepted Android licenses.
 */
interface AndroidUnacceptedLicenseHandler {
    /**
     * Handle unaccepted license of Android SDK component.
     *
     * @param packageDisplayName the display name of the package that resolved to the component requiring the [license]
     * @return `true` if the license was accepted by the user. `false`, otherwise.
     */
    context(_: OperationEventSink)
    suspend fun onUnacceptedLicense(
        packageDisplayName: String,
        license: AndroidLicense,
    ): Boolean

    object DontAccept : AndroidUnacceptedLicenseHandler {
        context(_: OperationEventSink)
        override suspend fun onUnacceptedLicense(packageDisplayName: String, license: AndroidLicense): Boolean = false
    }
}
