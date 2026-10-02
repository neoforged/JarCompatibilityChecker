/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.jarcompatibilitychecker.test;

import com.google.common.collect.ImmutableMap;
import net.neoforged.jarcompatibilitychecker.core.ClassInfoCache;
import net.neoforged.jarcompatibilitychecker.core.ClassInfoComparer;
import net.neoforged.jarcompatibilitychecker.core.ClassInfoComparisonResults;
import net.neoforged.jarcompatibilitychecker.core.IncompatibilityMessages;
import net.neoforged.jarcompatibilitychecker.data.ClassInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class ConstructorTests extends BaseCompatibilityTest {
    @ParameterizedTest(name = "checkBinary={0}")
    @ValueSource(booleans = {false, true})
    public void testRemovedConstructorIsNotInherited(boolean checkBinary) {
        ClassInfo baseClass = createClassWithConstructor("A", "()V");
        ClassInfo inputClass = createClassWithConstructor("A", "(I)V");

        ClassInfoCache baseCache = ClassInfoCache.fromMaps(ImmutableMap.of("A", baseClass), ImmutableMap.of());
        ClassInfoCache inputCache = ClassInfoCache.fromMaps(ImmutableMap.of("A", inputClass), ImmutableMap.of());
        ClassInfoComparisonResults results = ClassInfoComparer.compare(checkBinary, baseCache, baseClass, inputCache, inputClass);

        assertIncompatible(results, "A", "<init>", "()V", true, getExpectedMessage(checkBinary));
    }

    @ParameterizedTest(name = "checkBinary={0}")
    @ValueSource(booleans = {false, true})
    public void testRemovedConstructorIsNotInheritedFromCustomParent(boolean checkBinary) {
        ClassInfo baseParent = createClassWithConstructor("Parent", "(I)V");
        ClassInfo baseClass = createClassWithConstructor("A", "Parent", "(I)V");
        ClassInfo inputParent = createClassWithConstructor("Parent", "(I)V");
        ClassInfo inputClass = createClassWithConstructor("A", "Parent", "()V");

        ClassInfoCache baseCache = ClassInfoCache.fromMaps(ImmutableMap.of("Parent", baseParent, "A", baseClass), ImmutableMap.of());
        ClassInfoCache inputCache = ClassInfoCache.fromMaps(ImmutableMap.of("Parent", inputParent, "A", inputClass), ImmutableMap.of());
        ClassInfoComparisonResults results = ClassInfoComparer.compare(checkBinary, baseCache, baseClass, inputCache, inputClass);

        assertIncompatible(results, "A", "<init>", "(I)V", true, getExpectedMessage(checkBinary));
    }

    @ParameterizedTest(name = "checkBinary={0}")
    @ValueSource(booleans = {false, true})
    public void testConstructorVisibilityCanBeWidened(boolean checkBinary) {
        ClassInfo baseParent = createClassWithConstructor("Parent", "(I)V");
        ClassInfo baseClass = createClassWithConstructor("A", "Parent", "(I)V", Opcodes.ACC_PROTECTED);
        ClassInfo inputParent = createClassWithConstructor("Parent", "(I)V");
        ClassInfo inputClass = createClassWithConstructor("A", "Parent", "(I)V", Opcodes.ACC_PUBLIC);

        ClassInfoCache baseCache = ClassInfoCache.fromMaps(ImmutableMap.of("Parent", baseParent, "A", baseClass), ImmutableMap.of());
        ClassInfoCache inputCache = ClassInfoCache.fromMaps(ImmutableMap.of("Parent", inputParent, "A", inputClass), ImmutableMap.of());
        ClassInfoComparisonResults results = ClassInfoComparer.compare(checkBinary, baseCache, baseClass, inputCache, inputClass);

        assertCompatible(results, "A");
    }

    private static String getExpectedMessage(boolean checkBinary) {
        return checkBinary ? IncompatibilityMessages.METHOD_REMOVED : IncompatibilityMessages.API_METHOD_REMOVED;
    }

    private static ClassInfo createClassWithConstructor(String name, String constructorDesc) {
        return createClassWithConstructor(name, "java/lang/Object", constructorDesc);
    }

    private static ClassInfo createClassWithConstructor(String name, String superName, String constructorDesc) {
        return createClassWithConstructor(name, superName, constructorDesc, Opcodes.ACC_PUBLIC);
    }

    private static ClassInfo createClassWithConstructor(String name, String superName, String constructorDesc, int constructorAccess) {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = name;
        node.superName = superName;
        node.methods.add(new MethodNode(constructorAccess, "<init>", constructorDesc, null, null));
        return new ClassInfo(node);
    }
}
