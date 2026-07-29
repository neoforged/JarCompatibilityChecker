/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.jarcompatibilitychecker.core;

import com.google.common.collect.ImmutableList;
import net.neoforged.jarcompatibilitychecker.data.MemberInfo;
import net.neoforged.jarcompatibilitychecker.data.MethodInfo;

import java.util.List;

final class ApiStatusCompatibility {
    static final String NON_EXTENDABLE = "Lorg/jetbrains/annotations/ApiStatus$NonExtendable;";
    static final String INTERNAL = "Lorg/jetbrains/annotations/ApiStatus$Internal;";

    private ApiStatusCompatibility() {}

    static boolean isNonExtendableApiChange(boolean checkBinary, NonExtendableApiCheckMode nonExtendableApiCheckMode, List<String> nonExtendableApiAnnotations,
            MemberInfo memberInfo) {
        if (nonExtendableApiCheckMode == NonExtendableApiCheckMode.ERROR || checkBinary)
            return false;
        if (hasAnyAnnotation(memberInfo, nonExtendableApiAnnotations))
            return true;
        return memberInfo instanceof MethodInfo && hasAnyAnnotation(((MethodInfo) memberInfo).parent, nonExtendableApiAnnotations);
    }

    static boolean shouldSkip(NonExtendableApiCheckMode nonExtendableApiCheckMode, boolean nonExtendableApiChange) {
        return nonExtendableApiChange && nonExtendableApiCheckMode == NonExtendableApiCheckMode.SKIP;
    }

    static boolean shouldError(boolean defaultIsError, boolean nonExtendableApiChange) {
        return defaultIsError && !nonExtendableApiChange;
    }

    private static boolean hasAnyAnnotation(MemberInfo memberInfo, List<String> annotations) {
        for (String annotation : annotations) {
            if (memberInfo.hasAnnotation(annotation))
                return true;
        }

        return false;
    }

    static List<String> normalizeAnnotationDescriptors(List<String> annotations) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (String annotation : annotations) {
            builder.add(normalizeAnnotationDescriptor(annotation));
        }
        return builder.build();
    }

    private static String normalizeAnnotationDescriptor(String annotation) {
        boolean inDescForm = annotation.startsWith("L") && annotation.endsWith(";");
        return inDescForm ? annotation.replace('.', '/') : 'L' + annotation.replace('.', '/') + ';';
    }
}
