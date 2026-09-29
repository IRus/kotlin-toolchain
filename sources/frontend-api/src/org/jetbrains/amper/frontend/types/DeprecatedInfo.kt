/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

import org.jetbrains.annotations.Nls

/**
 * Deprecation info for a schema declaration (a property or an enum entry).
 *
 * @see org.jetbrains.amper.frontend.api.DeprecatedSchema
 */
data class DeprecatedInfo(
    val message: @Nls String,
    val isError: Boolean,
)
