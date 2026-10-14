/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.publishingSettings
import org.jetbrains.amper.frontend.schema.effectiveArtifactId

/**
 * The compiler module name stored as `unique_name` in a Native or web klib manifest, not its output file name.
 *
 * For conventional Gradle publications, this matches KGP's group-qualified klib identity.
 * Neither publishing enablement nor the publication version affects this identity. Test klibs get a separate name
 * because they are compiled and linked alongside the main klib.
 * [nameOverride] comes from the effective Kotlin settings of the compilation, including platform-specific settings.
 */
internal fun AmperModule.klibModuleName(isTest: Boolean, nameOverride: String?): String {
    val group = publishingSettings.group
    val baseName = nameOverride ?: if (group.isNullOrBlank()) {
        kotlinModuleName(isTest = false)
    } else {
        "$group:${publishingSettings.effectiveArtifactId(this)}"
    }
    val suffix = if (isTest) "_test" else ""
    return "$baseName$suffix"
}
