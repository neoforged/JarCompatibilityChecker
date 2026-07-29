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

import java.util.List;
import java.util.function.Consumer;

public class ApiStatusCompatibilityTests extends BaseCompatibilityTest {
    private static final String NON_EXTENDABLE = NonExtendableApiCheckMode.DEFAULT_NON_EXTENDABLE_API_ANNOTATIONS.get(0);
    private static final String CUSTOM_NON_EXTENDABLE = "Lcom/example/NonExtendable;";

    // Cases that ApiStatus compatibility can downgrade or suppress

    @Test
    public void testNonExtendableApiClassMadeFinalWarnsByDefault() {
        // Making a non-extendable public class final is reported as a warning by default
        fixtureComparison("Class/PublicClassMadeFinal", "A")
                .api()
                .withBaseClassAnnotation(NON_EXTENDABLE)
                .assertClassWarning(IncompatibilityMessages.CLASS_MADE_FINAL);
    }

    @Test
    public void testNonExtendableApiMethodMadeAbstractWarnsByDefault() {
        // Making a method abstract on a non-extendable type is reported as a warning by default
        fixtureComparison("Method/PublicMethodMadeAbstract", "A")
                .api()
                .withBaseClassAnnotation(NON_EXTENDABLE)
                .assertMemberWarning("thing", "()V", IncompatibilityMessages.METHOD_MADE_ABSTRACT);
    }

    @Test
    public void testNonExtendableApiMethodMadeFinalWarnsByDefault() {
        // Making a method final on a non-extendable type is reported as a warning by default
        fixtureComparison("Method/PublicMethodMadeFinal", "A")
                .api()
                .withBaseClassAnnotation(NON_EXTENDABLE)
                .assertMemberWarning("thing", "()V", IncompatibilityMessages.METHOD_MADE_FINAL);
    }

    @Test
    public void testNonExtendableMethodAnnotationAllowsMethodMadeFinal() {
        // A method can be marked non-extendable directly
        fixtureComparison("Method/PublicMethodMadeFinal", "A")
                .api()
                .withBaseMethodAnnotation("thing", "()V", NON_EXTENDABLE)
                .assertMemberWarning("thing", "()V", IncompatibilityMessages.METHOD_MADE_FINAL);
    }

    @Test
    public void testNonExtendableApiCheckModeSkipSuppressesAllowedIncompatibility() {
        // SKIP suppresses otherwise-allowed non-extendable API incompatibilities, matching internal API mode behavior
        fixtureComparison("Class/PublicClassMadeFinal", "A")
                .api()
                .withNonExtendableApiMode(NonExtendableApiCheckMode.SKIP)
                .withBaseClassAnnotation(NON_EXTENDABLE)
                .assertCompatible();
    }

    @Test
    public void testCustomNonExtendableAnnotationWarnsWhenConfigured() {
        // Custom non-extendable annotations apply when configured
        fixtureComparison("Class/PublicClassMadeFinal", "A")
                .api()
                .withNonExtendableApiAnnotations(ImmutableList.of(CUSTOM_NON_EXTENDABLE))
                .withBaseClassAnnotation(CUSTOM_NON_EXTENDABLE)
                .assertClassWarning(IncompatibilityMessages.CLASS_MADE_FINAL);
    }

    @Test
    public void testNonExtendableAnnotationBinaryNameIsNormalized() {
        // Configured annotations may use Java binary names instead of JVM descriptors
        fixtureComparison("Class/PublicClassMadeFinal", "A")
                .api()
                .withNonExtendableApiAnnotations(ImmutableList.of("org.jetbrains.annotations.ApiStatus$NonExtendable"))
                .withBaseClassAnnotation(NON_EXTENDABLE)
                .assertClassWarning(IncompatibilityMessages.CLASS_MADE_FINAL);
    }

    @Test
    public void testInternalApiStatusCanWarnUsingSeparateCheckMode() {
        // Internal API remains controlled by the internal annotation mode, separately from non-extendable API compatibility
        fixtureComparison("Class/InternalClassDeleted", "A")
                .api()
                .withInternalAnnotationMode(InternalAnnotationCheckMode.WARN)
                .assertClassWarning(IncompatibilityMessages.API_CLASS_MISSING);
    }

    // Cases that can be kept strict with ERROR mode

    @Test
    public void testNonExtendableApiClassMadeFinalInErrorModeRemainsError() {
        // ERROR mode preserves strict compatibility checks
        fixtureComparison("Class/PublicClassMadeFinal", "A")
                .api()
                .withNonExtendableApiMode(NonExtendableApiCheckMode.ERROR)
                .withBaseClassAnnotation(NON_EXTENDABLE)
                .assertClassError(IncompatibilityMessages.CLASS_MADE_FINAL);
    }

    @Test
    public void testNonExtendableApiMethodMadeAbstractInErrorModeRemainsError() {
        // ERROR mode preserves strict compatibility checks
        fixtureComparison("Method/PublicMethodMadeAbstract", "A")
                .api()
                .withNonExtendableApiMode(NonExtendableApiCheckMode.ERROR)
                .withBaseClassAnnotation(NON_EXTENDABLE)
                .assertMemberError("thing", "()V", IncompatibilityMessages.METHOD_MADE_ABSTRACT);
    }

    @Test
    public void testNonExtendableApiMethodMadeFinalInErrorModeRemainsError() {
        // ERROR mode preserves strict compatibility checks
        fixtureComparison("Method/PublicMethodMadeFinal", "A")
                .api()
                .withNonExtendableApiMode(NonExtendableApiCheckMode.ERROR)
                .withBaseClassAnnotation(NON_EXTENDABLE)
                .assertMemberError("thing", "()V", IncompatibilityMessages.METHOD_MADE_FINAL);
    }

    // Cases that should still report errors even with the most permissive non-extendable mode

    @Test
    public void testCustomNonExtendableAnnotationRequiresConfiguration() {
        // Custom non-extendable annotations only apply when configured
        fixtureComparison("Class/PublicClassMadeFinal", "A")
                .api()
                .withNonExtendableApiMode(NonExtendableApiCheckMode.SKIP)
                .withBaseClassAnnotation(CUSTOM_NON_EXTENDABLE)
                .assertClassError(IncompatibilityMessages.CLASS_MADE_FINAL);
    }

    @Test
    public void testNonExtendableBinaryClassMadeFinalRemainsError() {
        // Non-extendable API compatibility does not relax binary compatibility
        fixtureComparison("Class/PublicClassMadeFinal", "A")
                .binary()
                .withNonExtendableApiMode(NonExtendableApiCheckMode.SKIP)
                .withBaseClassAnnotation(NON_EXTENDABLE)
                .assertClassError(IncompatibilityMessages.CLASS_MADE_FINAL);
    }

    @Test
    public void testNonExtendableBinaryMethodMadeAbstractRemainsError() {
        // Non-extendable API compatibility does not relax binary compatibility
        fixtureComparison("Method/PublicMethodMadeAbstract", "A")
                .binary()
                .withNonExtendableApiMode(NonExtendableApiCheckMode.SKIP)
                .withBaseClassAnnotation(NON_EXTENDABLE)
                .assertMemberError("thing", "()V", IncompatibilityMessages.METHOD_MADE_ABSTRACT);
    }

    @Test
    public void testNonExtendableDoesNotAllowUnrelatedIncompatibilities() {
        // Non-extendable API compatibility only applies to extension-only incompatibilities
        fixtureComparison("Field/PublicFieldMadeFinal", "A")
                .api()
                .withNonExtendableApiMode(NonExtendableApiCheckMode.SKIP)
                .withBaseClassAnnotation(NON_EXTENDABLE)
                .assertMemberError("buzz", "Z", IncompatibilityMessages.FIELD_MADE_FINAL);
    }

    @Test
    public void testOuterNonExtendableAnnotationDoesNotApplyToNestedClass() {
        // Marking an outer class non-extendable does not mark its nested class non-extendable
        assertClassIncompatible(compareNestedClassMadeFinalWithNonExtendableOuter(), "Outer$Nested", IncompatibilityMessages.CLASS_MADE_FINAL);
    }

    @Test
    public void testInternalApiStatusCanRemainErrorUsingSeparateCheckMode() {
        // Internal API remains controlled by the internal annotation mode, separately from non-extendable API compatibility
        fixtureComparison("Class/InternalClassDeleted", "A")
                .api()
                .withNonExtendableApiMode(NonExtendableApiCheckMode.SKIP)
                .withInternalAnnotationMode(InternalAnnotationCheckMode.ERROR)
                .assertClassError(IncompatibilityMessages.API_CLASS_MISSING);
    }

    private FixtureComparisonBuilder fixtureComparison(String folderName, String className) {
        return new FixtureComparisonBuilder(folderName, className);
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

    private final class FixtureComparisonBuilder {
        private final String folderName;
        private final String className;
        private Boolean checkBinary;
        private InternalAnnotationCheckMode internalAnnotationCheckMode = InternalAnnotationCheckMode.ERROR;
        private NonExtendableApiCheckMode nonExtendableApiCheckMode = NonExtendableApiCheckMode.DEFAULT_MODE;
        private List<String> nonExtendableApiAnnotations = NonExtendableApiCheckMode.DEFAULT_NON_EXTENDABLE_API_ANNOTATIONS;
        private Consumer<ClassInfo> baseClassConfigurer = classInfo -> {};

        private FixtureComparisonBuilder(String folderName, String className) {
            this.folderName = folderName;
            this.className = className;
        }

        private FixtureComparisonBuilder api() {
            this.checkBinary = false;
            return this;
        }

        private FixtureComparisonBuilder binary() {
            this.checkBinary = true;
            return this;
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

        private FixtureComparisonBuilder withBaseClassAnnotation(String annotation) {
            return withBaseClass(classInfo -> annotate(classInfo, annotation));
        }

        private FixtureComparisonBuilder withBaseMethodAnnotation(String name, String desc, String annotation) {
            return withBaseClass(classInfo -> {
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
            Assert.assertNotNull("Compatibility mode must be configured with api() or binary()", this.checkBinary);
            return getComparisonResults(this.folderName, this.className, (baseCache, baseClassInfo, inputCache, inputClassInfo) -> {
                this.baseClassConfigurer.accept(baseClassInfo);
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
