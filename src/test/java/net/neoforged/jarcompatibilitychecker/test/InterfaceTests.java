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
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.file.Path;
import java.util.Arrays;

public class InterfaceTests extends BaseCompatibilityTest {
    @Override
    protected Path getRoot() {
        return super.getRoot().resolve("Interface");
    }

    @Test
    public void testApiInterfaceOrder() {
        // Interface order should not affect API compatibility
        assertCompatible(false, "InterfaceOrder", "A");
    }

    @Test
    public void testInterfaceOrder() {
        // Interface order should not affect binary compatibility
        assertCompatible(true, "InterfaceOrder", "A");
    }

    @Test
    public void testApiMissingPackagePrivateInterface() {
        // A missing package-private interface is API compatible
        assertCompatible(false, "MissingPackagePrivateInterface", "A");
    }

    @Test
    public void testMissingPackagePrivateInterface() {
        // A missing package-private interface is binary incompatible
        assertClassIncompatible(true, "MissingPackagePrivateInterface", "A", IncompatibilityMessages.CLASS_MISSING_INTERFACE, "B");
    }

    @Test
    public void testApiMissingPublicInterface() {
        // A missing public interface is API incompatible
        assertClassIncompatible(false, "MissingPublicInterface", "A", IncompatibilityMessages.CLASS_MISSING_INTERFACE, "B");
    }

    @Test
    public void testMissingPublicInterface() {
        // A missing public interface is binary incompatible
        assertClassIncompatible(true, "MissingPublicInterface", "A", IncompatibilityMessages.CLASS_MISSING_INTERFACE, "B");
    }

    @Test
    public void testApiNewInterface() {
        // A new interface is API compatible
        assertCompatible(false, "NewInterface", "A");
    }

    @Test
    public void testNewInterface() {
        // A new interface is binary compatible
        assertCompatible(true, "NewInterface", "A");
    }

    @Test
    public void testRedeclaredInheritedAbstractMethod() {
        ClassInfo baseParent = createInterface("A", Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT);
        ClassInfo baseChild = createInterface("B", 0, "A");

        ClassInfo inputParent = createInterface("A", Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT);
        ClassInfo inputChild = createInterface("B", Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "A");

        ClassInfoCache baseCache = ClassInfoCache.fromMaps(ImmutableMap.of("A", baseParent, "B", baseChild), ImmutableMap.of());
        ClassInfoCache inputCache = ClassInfoCache.fromMaps(ImmutableMap.of("A", inputParent, "B", inputChild), ImmutableMap.of());
        ClassInfoComparisonResults results = ClassInfoComparer.compare(true, baseCache, baseChild, inputCache, inputChild);

        assertCompatible(results, "B");
    }

    @Test
    public void testInheritedDefaultMethodMadeAbstract() {
        ClassInfo baseParent = createInterface("A", Opcodes.ACC_PUBLIC);
        ClassInfo baseChild = createInterface("B", 0, "A");

        ClassInfo inputParent = createInterface("A", Opcodes.ACC_PUBLIC);
        ClassInfo inputChild = createInterface("B", Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "A");

        ClassInfoCache baseCache = ClassInfoCache.fromMaps(ImmutableMap.of("A", baseParent, "B", baseChild), ImmutableMap.of());
        ClassInfoCache inputCache = ClassInfoCache.fromMaps(ImmutableMap.of("A", inputParent, "B", inputChild), ImmutableMap.of());
        ClassInfoComparisonResults results = ClassInfoComparer.compare(true, baseCache, baseChild, inputCache, inputChild);

        assertIncompatible(results, "B", "abstractMethod", "()V", true, IncompatibilityMessages.METHOD_MADE_ABSTRACT);
    }

    private static ClassInfo createInterface(String name, int methodAccess, String... interfaces) {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT;
        node.name = name;
        node.superName = "java/lang/Object";
        node.interfaces.addAll(Arrays.asList(interfaces));
        if (methodAccess != 0) {
            node.methods.add(new MethodNode(methodAccess, "abstractMethod", "()V", null, null));
        }
        return new ClassInfo(node);
    }
}
