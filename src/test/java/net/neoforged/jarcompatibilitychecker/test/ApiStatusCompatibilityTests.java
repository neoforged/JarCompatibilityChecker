/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.jarcompatibilitychecker.test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.neoforged.jarcompatibilitychecker.core.ClassInfoCache;
import net.neoforged.jarcompatibilitychecker.core.ClassInfoComparer;
import net.neoforged.jarcompatibilitychecker.core.ClassInfoComparisonResults;
import net.neoforged.jarcompatibilitychecker.core.IncompatibilityMessages;
import net.neoforged.jarcompatibilitychecker.core.InternalAnnotationCheckMode;
import net.neoforged.jarcompatibilitychecker.core.NonExtendableApiCheckMode;
import net.neoforged.jarcompatibilitychecker.data.AnnotationInfo;
import net.neoforged.jarcompatibilitychecker.data.ClassInfo;
import net.neoforged.jarcompatibilitychecker.data.MemberInfo;
import net.neoforged.jarcompatibilitychecker.data.MethodInfo;
import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.function.Consumer;

public class ApiStatusCompatibilityTests extends BaseCompatibilityTest {
    private static final String INTERNAL = InternalAnnotationCheckMode.DEFAULT_INTERNAL_ANNOTATIONS.get(0);
    private static final String NON_EXTENDABLE = NonExtendableApiCheckMode.DEFAULT_NON_EXTENDABLE_API_ANNOTATIONS.get(0);
    private static final String CUSTOM_NON_EXTENDABLE = "Lcom/example/NonExtendable;";
    private static final String METHOD_NAME = "thing";
    private static final String METHOD_DESC = "()V";

    // Cases that ApiStatus compatibility can downgrade or suppress

    @Test
    public void nonExtendableApiClassMadeFinalWarnsByDefault() {
        // Making a public class final breaks external subclasses, but @NonExtendable
        // marks subclassing as not supported, so the break is downgraded to a warning.
        fixtureComparison("Class/PublicClassMadeFinal", "A")
                .api()
                .withClassAnnotation(NON_EXTENDABLE)
                .assertClassWarning(IncompatibilityMessages.CLASS_MADE_FINAL);
    }

    @Test
    public void nonExtendableApiMethodMadeAbstractWarnsByDefault() {
        // Making a method abstract breaks subclasses that rely on the implementation,
        // but @NonExtendable marks subclassing as not supported, so the break is
        // downgraded to a warning.
        fixtureComparison("Method/PublicMethodMadeAbstract", "A")
                .api()
                .withClassAnnotation(NON_EXTENDABLE)
                .assertMemberWarning(METHOD_NAME, METHOD_DESC, IncompatibilityMessages.METHOD_MADE_ABSTRACT);
    }

    @Test
    public void nonExtendableApiMethodMadeFinalWarnsByDefault() {
        // Making a method final breaks overrides, but @NonExtendable marks subclassing
        // as not supported, so the break is downgraded to a warning.
        fixtureComparison("Method/PublicMethodMadeFinal", "A")
                .api()
                .withClassAnnotation(NON_EXTENDABLE)
                .assertMemberWarning(METHOD_NAME, METHOD_DESC, IncompatibilityMessages.METHOD_MADE_FINAL);
    }

    @Test
    public void nonExtendableMethodAnnotationAllowsMethodMadeFinal() {
        // Making a method final breaks overrides, but method-level @NonExtendable
        // marks overriding as not supported, so the break is downgraded to a warning.
        fixtureComparison("Method/PublicMethodMadeFinal", "A")
                .api()
                .withMethodAnnotation(METHOD_NAME, METHOD_DESC, NON_EXTENDABLE)
                .assertMemberWarning(METHOD_NAME, METHOD_DESC, IncompatibilityMessages.METHOD_MADE_FINAL);
    }

    @Test
    public void nonExtendableApiCheckModeSkipSuppressesAllowedIncompatibility() {
        // SKIP mode suppresses extension-only breaks when the API marks extension as
        // not supported, so the result remains compatible.
        fixtureComparison("Class/PublicClassMadeFinal", "A")
                .api()
                .withNonExtendableApiMode(NonExtendableApiCheckMode.SKIP)
                .withClassAnnotation(NON_EXTENDABLE)
                .assertCompatible();
    }

    @Test
    public void customNonExtendableAnnotationWarnsWhenConfigured() {
        // Configured custom markers use the same contract as @NonExtendable, so
        // extension-only breaks are downgraded to warnings.
        fixtureComparison("Class/PublicClassMadeFinal", "A")
                .api()
                .withNonExtendableApiAnnotations(ImmutableList.of(CUSTOM_NON_EXTENDABLE))
                .withClassAnnotation(CUSTOM_NON_EXTENDABLE)
                .assertClassWarning(IncompatibilityMessages.CLASS_MADE_FINAL);
    }

    @Test
    public void nonExtendableAnnotationBinaryNameIsNormalized() {
        // Binary-name configuration resolves to the same marker descriptor, so the
        // same @NonExtendable compatibility policy applies.
        fixtureComparison("Class/PublicClassMadeFinal", "A")
                .api()
                .withNonExtendableApiAnnotations(ImmutableList.of("org.jetbrains.annotations.ApiStatus$NonExtendable"))
                .withClassAnnotation(NON_EXTENDABLE)
                .assertClassWarning(IncompatibilityMessages.CLASS_MADE_FINAL);
    }

    @Test
    public void internalApiStatusCanWarnUsingSeparateCheckMode() {
        // Deleting a visible class breaks callers, but @Internal marks the class as
        // not part of the supported API, so WARN mode downgrades the break to a warning.
        fixtureComparison("Class/InternalClassDeleted", "A")
                .api()
                .withInternalAnnotationMode(InternalAnnotationCheckMode.WARN)
                .assertClassWarning(IncompatibilityMessages.API_CLASS_MISSING);
    }

    // ApiStatus marker annotations are compatibility-affecting API contract changes

    @Test
    public void internalAnnotationAddedToClassIsReportedAsError() {
        // Adding @Internal removes the class from the supported API contract, so the
        // annotation change is reported as an error.
        assertClassIncompatible(
                compareApiStatusAnnotationChange(publicClass("A"), publicClass("A", INTERNAL)),
                "A",
                IncompatibilityMessages.ANNOTATION_ADDED
        );
    }

    @Test
    public void internalAnnotationRemovedFromClassWarnsByDefault() {
        // Removing @Internal adds the class to the supported API contract. That
        // widens the contract, so the annotation change is reported as a warning.
        assertClassIncompatible(
                compareApiStatusAnnotationChange(publicClass("A", INTERNAL), publicClass("A")),
                "A",
                false,
                IncompatibilityMessages.ANNOTATION_REMOVED
        );
    }

    @Test
    public void internalAnnotationAddedToMethodIsReportedAsError() {
        // Adding @Internal removes the method from the supported API contract, so the
        // annotation change is reported as an error.
        assertIncompatible(
                compareApiStatusAnnotationChange(publicClassWithPublicMethod("A"), publicClassWithAnnotatedPublicMethod("A", INTERNAL)),
                "A",
                METHOD_NAME,
                METHOD_DESC,
                true,
                IncompatibilityMessages.ANNOTATION_ADDED
        );
    }

    @Test
    public void internalAnnotationRemovedFromMethodWarnsByDefault() {
        // Removing @Internal adds the method to the supported API contract. That
        // widens the contract, so the annotation change is reported as a warning.
        assertIncompatible(
                compareApiStatusAnnotationChange(publicClassWithAnnotatedPublicMethod("A", INTERNAL), publicClassWithPublicMethod("A")),
                "A",
                METHOD_NAME,
                METHOD_DESC,
                false,
                IncompatibilityMessages.ANNOTATION_REMOVED
        );
    }

    @Test
    public void nonExtendableAnnotationAddedToClassIsReportedAsError() {
        // Adding @NonExtendable removes supported subclassing from the class contract,
        // so the annotation change is reported as an error.
        assertClassIncompatible(
                compareApiStatusAnnotationChange(publicClass("A"), publicClass("A", NON_EXTENDABLE)),
                "A",
                IncompatibilityMessages.ANNOTATION_ADDED
        );
    }

    @Test
    public void nonExtendableAnnotationRemovedFromClassWarnsByDefault() {
        // Removing @NonExtendable adds supported subclassing to the class contract.
        // That widens the contract, so the annotation change is reported as a warning.
        assertClassIncompatible(
                compareApiStatusAnnotationChange(publicClass("A", NON_EXTENDABLE), publicClass("A")),
                "A",
                false,
                IncompatibilityMessages.ANNOTATION_REMOVED
        );
    }

    @Test
    public void nonExtendableAnnotationAddedToMethodIsReportedAsError() {
        // Adding @NonExtendable removes supported overriding from the method contract,
        // so the annotation change is reported as an error.
        assertIncompatible(
                compareApiStatusAnnotationChange(publicClassWithPublicMethod("A"), publicClassWithAnnotatedPublicMethod("A", NON_EXTENDABLE)),
                "A",
                METHOD_NAME,
                METHOD_DESC,
                true,
                IncompatibilityMessages.ANNOTATION_ADDED
        );
    }

    @Test
    public void nonExtendableAnnotationRemovedFromMethodWarnsByDefault() {
        // Removing @NonExtendable adds supported overriding to the method contract.
        // That widens the contract, so the annotation change is reported as a warning.
        assertIncompatible(
                compareApiStatusAnnotationChange(publicClassWithAnnotatedPublicMethod("A", NON_EXTENDABLE), publicClassWithPublicMethod("A")),
                "A",
                METHOD_NAME,
                METHOD_DESC,
                false,
                IncompatibilityMessages.ANNOTATION_REMOVED
        );
    }

    // Cases that can be kept strict with ERROR mode

    @Test
    public void nonExtendableApiClassMadeFinalInErrorModeRemainsError() {
        // ERROR mode keeps extension-only breaks as errors, so finalizing the class
        // reports a subclassing compatibility break.
        fixtureComparison("Class/PublicClassMadeFinal", "A")
                .api()
                .withNonExtendableApiMode(NonExtendableApiCheckMode.ERROR)
                .withClassAnnotation(NON_EXTENDABLE)
                .assertClassError(IncompatibilityMessages.CLASS_MADE_FINAL);
    }

    @Test
    public void nonExtendableApiMethodMadeAbstractInErrorModeRemainsError() {
        // ERROR mode keeps extension-only breaks as errors, so making the method
        // abstract reports a subclassing compatibility break.
        fixtureComparison("Method/PublicMethodMadeAbstract", "A")
                .api()
                .withNonExtendableApiMode(NonExtendableApiCheckMode.ERROR)
                .withClassAnnotation(NON_EXTENDABLE)
                .assertMemberError(METHOD_NAME, METHOD_DESC, IncompatibilityMessages.METHOD_MADE_ABSTRACT);
    }

    @Test
    public void nonExtendableApiMethodMadeFinalInErrorModeRemainsError() {
        // ERROR mode keeps extension-only breaks as errors, so making the method final
        // reports an overriding compatibility break.
        fixtureComparison("Method/PublicMethodMadeFinal", "A")
                .api()
                .withNonExtendableApiMode(NonExtendableApiCheckMode.ERROR)
                .withClassAnnotation(NON_EXTENDABLE)
                .assertMemberError(METHOD_NAME, METHOD_DESC, IncompatibilityMessages.METHOD_MADE_FINAL);
    }

    // Cases that should still report errors even with the most permissive non-extendable mode

    @Test
    public void customNonExtendableAnnotationRequiresConfiguration() {
        // Unconfigured custom markers have no compatibility policy, so finalizing the
        // class reports a subclassing compatibility break.
        fixtureComparison("Class/PublicClassMadeFinal", "A")
                .api()
                .withNonExtendableApiMode(NonExtendableApiCheckMode.SKIP)
                .withClassAnnotation(CUSTOM_NON_EXTENDABLE)
                .assertClassError(IncompatibilityMessages.CLASS_MADE_FINAL);
    }

    @Test
    public void nonExtendableBinaryClassMadeFinalRemainsError() {
        // Binary mode checks JVM compatibility, so API policy markers cannot downgrade
        // final-class breaks to warnings.
        fixtureComparison("Class/PublicClassMadeFinal", "A")
                .binary()
                .withNonExtendableApiMode(NonExtendableApiCheckMode.SKIP)
                .withClassAnnotation(NON_EXTENDABLE)
                .assertClassError(IncompatibilityMessages.CLASS_MADE_FINAL);
    }

    @Test
    public void nonExtendableBinaryMethodMadeAbstractRemainsError() {
        // Binary mode checks JVM compatibility, so API policy markers cannot downgrade
        // abstract-method breaks to warnings.
        fixtureComparison("Method/PublicMethodMadeAbstract", "A")
                .binary()
                .withNonExtendableApiMode(NonExtendableApiCheckMode.SKIP)
                .withClassAnnotation(NON_EXTENDABLE)
                .assertMemberError(METHOD_NAME, METHOD_DESC, IncompatibilityMessages.METHOD_MADE_ABSTRACT);
    }

    @Test
    public void nonExtendableDoesNotAllowUnrelatedIncompatibilities() {
        // Making a public field final is not an extension-only break, so
        // @NonExtendable cannot downgrade or suppress it.
        fixtureComparison("Field/PublicFieldMadeFinal", "A")
                .api()
                .withNonExtendableApiMode(NonExtendableApiCheckMode.SKIP)
                .withClassAnnotation(NON_EXTENDABLE)
                .assertMemberError("buzz", "Z", IncompatibilityMessages.FIELD_MADE_FINAL);
    }

    @Test
    public void outerNonExtendableAnnotationDoesNotApplyToNestedClass() {
        // A nested class is a separate API element, so an outer @NonExtendable marker
        // cannot downgrade or suppress the nested class's subclassing break.
        assertClassIncompatible(compareNestedClassMadeFinalWithNonExtendableOuter(), "Outer$Nested", IncompatibilityMessages.CLASS_MADE_FINAL);
    }

    @Test
    public void internalApiStatusCanRemainErrorUsingSeparateCheckMode() {
        // ERROR mode keeps @Internal elements in strict API checks, so deleting the
        // class reports a compatibility error.
        fixtureComparison("Class/InternalClassDeleted", "A")
                .api()
                .withNonExtendableApiMode(NonExtendableApiCheckMode.SKIP)
                .withInternalAnnotationMode(InternalAnnotationCheckMode.ERROR)
                .assertClassError(IncompatibilityMessages.API_CLASS_MISSING);
    }

    private FixtureComparisonModeBuilder fixtureComparison(String folderName, String className) {
        return new FixtureComparisonModeBuilder(folderName, className);
    }

    private ClassInfoComparisonResults compareNestedClassMadeFinalWithNonExtendableOuter() {
        ClassInfo outerClass = publicClass("Outer", NON_EXTENDABLE);
        ClassInfo baseNestedClass = publicClass("Outer$Nested");
        ClassInfo inputNestedClass = publicFinalClass("Outer$Nested");

        ClassInfoCache baseCache = ClassInfoCache.fromMaps(ImmutableMap.of(
                outerClass.getName(), outerClass,
                baseNestedClass.getName(), baseNestedClass
        ), ImmutableMap.of());
        ClassInfoCache inputCache = ClassInfoCache.fromMaps(ImmutableMap.of(inputNestedClass.getName(), inputNestedClass), ImmutableMap.of());

        return ClassInfoComparer.compare(
                false,
                null,
                InternalAnnotationCheckMode.DEFAULT_INTERNAL_ANNOTATIONS,
                InternalAnnotationCheckMode.ERROR,
                NonExtendableApiCheckMode.SKIP,
                NonExtendableApiCheckMode.DEFAULT_NON_EXTENDABLE_API_ANNOTATIONS,
                baseCache,
                baseNestedClass,
                inputCache,
                inputNestedClass
        );
    }

    private ClassInfoComparisonResults compareApiStatusAnnotationChange(ClassInfo baseClass, ClassInfo inputClass) {
        ClassInfoCache baseCache = ClassInfoCache.fromMaps(ImmutableMap.of(baseClass.getName(), baseClass), ImmutableMap.of());
        ClassInfoCache inputCache = ClassInfoCache.fromMaps(ImmutableMap.of(inputClass.getName(), inputClass), ImmutableMap.of());

        return ClassInfoComparer.compare(
                false,
                null,
                InternalAnnotationCheckMode.DEFAULT_INTERNAL_ANNOTATIONS,
                InternalAnnotationCheckMode.DEFAULT_MODE,
                NonExtendableApiCheckMode.DEFAULT_MODE,
                NonExtendableApiCheckMode.DEFAULT_NON_EXTENDABLE_API_ANNOTATIONS,
                baseCache,
                baseClass,
                inputCache,
                inputClass
        );
    }

    private final class FixtureComparisonModeBuilder {
        private final String folderName;
        private final String className;

        private FixtureComparisonModeBuilder(String folderName, String className) {
            this.folderName = folderName;
            this.className = className;
        }

        private FixtureComparisonBuilder api() {
            return new FixtureComparisonBuilder(this.folderName, this.className, false);
        }

        private FixtureComparisonBuilder binary() {
            return new FixtureComparisonBuilder(this.folderName, this.className, true);
        }
    }

    private final class FixtureComparisonBuilder {
        private final String folderName;
        private final String className;
        private final boolean checkBinary;
        private InternalAnnotationCheckMode internalAnnotationCheckMode = InternalAnnotationCheckMode.ERROR;
        private NonExtendableApiCheckMode nonExtendableApiCheckMode = NonExtendableApiCheckMode.DEFAULT_MODE;
        private List<String> nonExtendableApiAnnotations = NonExtendableApiCheckMode.DEFAULT_NON_EXTENDABLE_API_ANNOTATIONS;
        private Consumer<ClassInfo> baseClassConfigurer = classInfo -> {};
        private Consumer<ClassInfo> inputClassConfigurer = classInfo -> {};

        private FixtureComparisonBuilder(String folderName, String className, boolean checkBinary) {
            this.folderName = folderName;
            this.className = className;
            this.checkBinary = checkBinary;
        }

        private FixtureComparisonBuilder withInternalAnnotationMode(InternalAnnotationCheckMode internalAnnotationCheckMode) {
            this.internalAnnotationCheckMode = internalAnnotationCheckMode;
            return this;
        }

        private FixtureComparisonBuilder withNonExtendableApiMode(NonExtendableApiCheckMode nonExtendableApiCheckMode) {
            this.nonExtendableApiCheckMode = nonExtendableApiCheckMode;
            return this;
        }

        private FixtureComparisonBuilder withNonExtendableApiAnnotations(List<String> nonExtendableApiAnnotations) {
            this.nonExtendableApiAnnotations = nonExtendableApiAnnotations;
            return this;
        }

        private FixtureComparisonBuilder withClassAnnotation(String annotation) {
            return withBaseClassAnnotation(annotation).withInputClassAnnotation(annotation);
        }

        private FixtureComparisonBuilder withBaseClassAnnotation(String annotation) {
            return withBaseClass(classInfo -> annotate(classInfo, annotation));
        }

        private FixtureComparisonBuilder withInputClassAnnotation(String annotation) {
            return withInputClass(classInfo -> annotate(classInfo, annotation));
        }

        private FixtureComparisonBuilder withMethodAnnotation(String name, String desc, String annotation) {
            return withBaseMethodAnnotation(name, desc, annotation).withInputMethodAnnotation(name, desc, annotation);
        }

        private FixtureComparisonBuilder withBaseMethodAnnotation(String name, String desc, String annotation) {
            return withBaseClass(classInfo -> {
                MethodInfo methodInfo = classInfo.getMethod(name, desc);
                Assert.assertNotNull("Method " + name + desc + " not found", methodInfo);
                annotate(methodInfo, annotation);
            });
        }

        private FixtureComparisonBuilder withInputMethodAnnotation(String name, String desc, String annotation) {
            return withInputClass(classInfo -> {
                MethodInfo methodInfo = classInfo.getMethod(name, desc);
                Assert.assertNotNull("Method " + name + desc + " not found", methodInfo);
                annotate(methodInfo, annotation);
            });
        }

        private FixtureComparisonBuilder withBaseClass(Consumer<ClassInfo> configurer) {
            Consumer<ClassInfo> previousConfigurer = this.baseClassConfigurer;
            this.baseClassConfigurer = classInfo -> {
                previousConfigurer.accept(classInfo);
                configurer.accept(classInfo);
            };
            return this;
        }

        private FixtureComparisonBuilder withInputClass(Consumer<ClassInfo> configurer) {
            Consumer<ClassInfo> previousConfigurer = this.inputClassConfigurer;
            this.inputClassConfigurer = classInfo -> {
                previousConfigurer.accept(classInfo);
                configurer.accept(classInfo);
            };
            return this;
        }

        private void assertClassError(String message) {
            assertClassIncompatible(results(), this.className, message);
        }

        private void assertClassWarning(String message) {
            assertClassIncompatible(results(), this.className, false, message);
        }

        private void assertMemberError(String name, String desc, String message) {
            assertIncompatible(results(), this.className, name, desc, true, message);
        }

        private void assertMemberWarning(String name, String desc, String message) {
            assertIncompatible(results(), this.className, name, desc, false, message);
        }

        private void assertCompatible() {
            ApiStatusCompatibilityTests.this.assertCompatible(results(), this.className);
        }

        private ClassInfoComparisonResults results() {
            return getComparisonResults(this.folderName, this.className, (baseCache, baseClassInfo, inputCache, inputClassInfo) -> {
                this.baseClassConfigurer.accept(baseClassInfo);
                if (inputClassInfo != null) {
                    this.inputClassConfigurer.accept(inputClassInfo);
                }
                return ClassInfoComparer.compare(
                        this.checkBinary,
                        null,
                        InternalAnnotationCheckMode.DEFAULT_INTERNAL_ANNOTATIONS,
                        this.internalAnnotationCheckMode,
                        this.nonExtendableApiCheckMode,
                        this.nonExtendableApiAnnotations,
                        baseCache,
                        baseClassInfo,
                        inputCache,
                        inputClassInfo
                );
            });
        }
    }

    private static void annotate(MemberInfo memberInfo, String annotation) {
        memberInfo.getAnnotations().add(new AnnotationInfo(annotation, ImmutableList.of()));
    }

    private static ClassInfo publicClass(String name, String... annotations) {
        return classInfo(name, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, annotations);
    }

    private static ClassInfo publicFinalClass(String name, String... annotations) {
        return classInfo(name, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER | Opcodes.ACC_FINAL, annotations);
    }

    private static ClassInfo publicClassWithAnnotatedPublicMethod(String name, String annotation) {
        ClassInfo classInfo = publicClassWithPublicMethod(name);
        MethodInfo methodInfo = classInfo.getMethod(METHOD_NAME, METHOD_DESC);
        Assert.assertNotNull("Method " + METHOD_NAME + METHOD_DESC + " not found", methodInfo);
        annotate(methodInfo, annotation);
        return classInfo;
    }

    private static ClassInfo publicClassWithPublicMethod(String name) {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER;
        node.name = name;
        node.superName = "java/lang/Object";
        node.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, METHOD_NAME, METHOD_DESC, null, null));
        return new ClassInfo(node);
    }

    private static ClassInfo classInfo(String name, int access, String... annotations) {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V1_8;
        node.access = access;
        node.name = name;
        node.superName = "java/lang/Object";

        ClassInfo classInfo = new ClassInfo(node);
        for (String annotation : annotations) {
            annotate(classInfo, annotation);
        }
        return classInfo;
    }
}
