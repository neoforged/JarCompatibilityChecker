/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.jarcompatibilitychecker.core;

import com.google.common.collect.ImmutableList;
import net.neoforged.jarcompatibilitychecker.data.AnnotationInfo;
import net.neoforged.jarcompatibilitychecker.data.MemberInfo;
import net.neoforged.jarcompatibilitychecker.data.MethodInfo;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    static List<String> mergeAnnotationDescriptors(List<String> firstAnnotations, List<String> secondAnnotations) {
        Set<String> annotations = new LinkedHashSet<>();
        annotations.addAll(firstAnnotations);
        annotations.addAll(secondAnnotations);
        return ImmutableList.copyOf(annotations);
    }

    static <I extends MemberInfo> void checkApiStatusAnnotationChanges(ClassInfoComparisonResults results, I memberInfo, boolean isError,
            List<String> apiStatusAnnotations, List<AnnotationInfo> baseAnnotations, List<AnnotationInfo> concreteAnnotations) {
        if (apiStatusAnnotations.isEmpty() || (baseAnnotations.isEmpty() && concreteAnnotations.isEmpty()))
            return;

        for (String apiStatusAnnotation : apiStatusAnnotations) {
            AnnotationInfo baseAnnotation = findAnnotation(baseAnnotations, apiStatusAnnotation);
            AnnotationInfo concreteAnnotation = findAnnotation(concreteAnnotations, apiStatusAnnotation);
            if (baseAnnotation == null && concreteAnnotation != null) {
                results.addAnnotationIncompatibility(memberInfo, concreteAnnotation, IncompatibilityMessages.ANNOTATION_ADDED, isError);
            } else if (baseAnnotation != null && concreteAnnotation == null) {
                // Removing an API-status marker widens the supported contract rather
                // than breaking existing supported callers. Keep the contract change
                // visible, but report it as a warning by default.
                results.addAnnotationIncompatibility(memberInfo, baseAnnotation, IncompatibilityMessages.ANNOTATION_REMOVED, false);
            }
        }
    }

    private static AnnotationInfo findAnnotation(List<AnnotationInfo> annotations, String desc) {
        for (AnnotationInfo annotation : annotations) {
            if (desc.equals(annotation.desc))
                return annotation;
        }

        return null;
    }

    private static String normalizeAnnotationDescriptor(String annotation) {
        boolean inDescForm = annotation.startsWith("L") && annotation.endsWith(";");
        return inDescForm ? annotation.replace('.', '/') : 'L' + annotation.replace('.', '/') + ';';
    }
}
