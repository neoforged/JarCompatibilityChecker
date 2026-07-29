/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.jarcompatibilitychecker.core;

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * This enum determines the check mode for extension-only incompatibilities on elements marked with non-extendable API annotations.
 * A non-extendable API annotation can be used to mark a public API element as unsupported for external implementation, subclassing, or overriding.
 * <p>
 * By default, the {@linkplain #WARN} mode is used so extension-only incompatibilities are reported without failing compatibility checks.
 */
public enum NonExtendableApiCheckMode {
    /**
     * No extension-only incompatibilities will be reported for elements annotated with a non-extendable API annotation.
     */
    SKIP,
    /**
     * Extension-only incompatibilities will be lowered to warnings for elements annotated with a non-extendable API annotation.
     */
    WARN,
    /**
     * Error-level incompatibilities will be raised regardless of being marked with a non-extendable API annotation.
     */
    ERROR;

    public static final NonExtendableApiCheckMode DEFAULT_MODE = WARN;
    public static final List<String> DEFAULT_NON_EXTENDABLE_API_ANNOTATIONS = ImmutableList.of(ApiStatusCompatibility.NON_EXTENDABLE);
}
