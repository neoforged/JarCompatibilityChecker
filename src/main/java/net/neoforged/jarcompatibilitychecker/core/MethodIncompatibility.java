/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.jarcompatibilitychecker.core;

import net.neoforged.jarcompatibilitychecker.data.MethodInfo;

public class MethodIncompatibility extends BaseIncompatibility<MethodInfo> {
    public MethodIncompatibility(MethodInfo methodInfo, String message, boolean isError) {
        super(methodInfo, message, isError);
    }

    @Override
    public String toString() {
        return this.memberInfo + " - " + this.message;
    }
}
