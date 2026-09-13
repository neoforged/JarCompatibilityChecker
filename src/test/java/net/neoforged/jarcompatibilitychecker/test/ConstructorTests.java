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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

@RunWith(Parameterized.class)
public class ConstructorTests extends BaseCompatibilityTest {
    @Parameterized.Parameters(name = "checkBinary={0}")
    public static Object[][] parameters() {
        return new Object[][] {{false}, {true}};
    }

    private final boolean checkBinary;

    public ConstructorTests(boolean checkBinary) {
        this.checkBinary = checkBinary;
    }

    @Test
    public void testRemovedConstructorIsNotInherited() {
        ClassInfo baseClass = createClassWithConstructor("A", "()V");
        ClassInfo inputClass = createClassWithConstructor("A", "(I)V");

        ClassInfoCache baseCache = ClassInfoCache.fromMaps(ImmutableMap.of("A", baseClass), ImmutableMap.of());
        ClassInfoCache inputCache = ClassInfoCache.fromMaps(ImmutableMap.of("A", inputClass), ImmutableMap.of());
        ClassInfoComparisonResults results = ClassInfoComparer.compare(this.checkBinary, baseCache, baseClass, inputCache, inputClass);

        assertIncompatible(results, "A", "<init>", "()V", true, getExpectedMessage());
    }

    @Test
    public void testRemovedConstructorIsNotInheritedFromCustomParent() {
        ClassInfo baseParent = createClassWithConstructor("Parent", "(I)V");
        ClassInfo baseClass = createClassWithConstructor("A", "Parent", "(I)V");
        ClassInfo inputParent = createClassWithConstructor("Parent", "(I)V");
        ClassInfo inputClass = createClassWithConstructor("A", "Parent", "()V");

        ClassInfoCache baseCache = ClassInfoCache.fromMaps(ImmutableMap.of("Parent", baseParent, "A", baseClass), ImmutableMap.of());
        ClassInfoCache inputCache = ClassInfoCache.fromMaps(ImmutableMap.of("Parent", inputParent, "A", inputClass), ImmutableMap.of());
        ClassInfoComparisonResults results = ClassInfoComparer.compare(this.checkBinary, baseCache, baseClass, inputCache, inputClass);

        assertIncompatible(results, "A", "<init>", "(I)V", true, getExpectedMessage());
    }

    @Test
    public void testConstructorVisibilityCanBeWidened() {
        ClassInfo baseParent = createClassWithConstructor("Parent", "(I)V");
        ClassInfo baseClass = createClassWithConstructor("A", "Parent", "(I)V", Opcodes.ACC_PROTECTED);
        ClassInfo inputParent = createClassWithConstructor("Parent", "(I)V");
        ClassInfo inputClass = createClassWithConstructor("A", "Parent", "(I)V", Opcodes.ACC_PUBLIC);

        ClassInfoCache baseCache = ClassInfoCache.fromMaps(ImmutableMap.of("Parent", baseParent, "A", baseClass), ImmutableMap.of());
        ClassInfoCache inputCache = ClassInfoCache.fromMaps(ImmutableMap.of("Parent", inputParent, "A", inputClass), ImmutableMap.of());
        ClassInfoComparisonResults results = ClassInfoComparer.compare(this.checkBinary, baseCache, baseClass, inputCache, inputClass);

        assertCompatible(results, "A");
    }

    private String getExpectedMessage() {
        return this.checkBinary ? IncompatibilityMessages.METHOD_REMOVED : IncompatibilityMessages.API_METHOD_REMOVED;
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
