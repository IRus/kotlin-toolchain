/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.android.sdk.provisioning

import org.jetbrains.amper.core.UsedInIdePlugin
import java.nio.file.Path

/**
 * Package path (notation) in the Android SDK repository notation.
 *
 * Examples:
 * - `platforms;android-37.0`
 * - `ndk;27.0.11902837`
 * - `build-tools;37.0.0`
 */
@JvmInline
@UsedInIdePlugin
value class PackagePath(val path: String) {
    override fun toString(): String = path
}

@UsedInIdePlugin
data class AndroidSdkPackage(
    /**
     * Notation of the package.
     */
    val packagePath: PackagePath,
    /**
     * The root directory of the installed package.
     */
    val location: Path,
    /**
     * The license required by the package.
     */
    val license: AndroidLicense,
)

@JvmInline
@UsedInIdePlugin
value class AndroidLicenseId(val value: String) {
    override fun toString(): String = value
}

@UsedInIdePlugin
interface AndroidLicense {
    val licenseId: AndroidLicenseId

    @Deprecated(
        "Use value class for referencing the ID instead.",
        replaceWith = ReplaceWith("licenseId.value"),
        level = DeprecationLevel.ERROR,
    )
    val id: String
        get() = licenseId.value

    val text: String

    fun isAccepted(): Boolean
}