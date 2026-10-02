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

public class ClassKindTests extends BaseCompatibilityTest {
    private static final int ABSTRACT_CLASS = Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT;
    private static final int INTERFACE = Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT;

    @ParameterizedTest(name = "checkBinary={0}")
    @ValueSource(booleans = {false, true})
    public void testClassChangedToInterface(boolean checkBinary) {
        assertClassIncompatible(compare(checkBinary, ABSTRACT_CLASS, INTERFACE), "A", IncompatibilityMessages.CLASS_CHANGED_KIND);
    }

    @ParameterizedTest(name = "checkBinary={0}")
    @ValueSource(booleans = {false, true})
    public void testInterfaceChangedToClass(boolean checkBinary) {
        assertClassIncompatible(compare(checkBinary, INTERFACE, ABSTRACT_CLASS), "A", IncompatibilityMessages.CLASS_CHANGED_KIND);
    }

    @ParameterizedTest(name = "checkBinary={0}")
    @ValueSource(booleans = {false, true})
    public void testClassRemainsClass(boolean checkBinary) {
        assertCompatible(compare(checkBinary, ABSTRACT_CLASS, ABSTRACT_CLASS), "A");
    }

    @ParameterizedTest(name = "checkBinary={0}")
    @ValueSource(booleans = {false, true})
    public void testInterfaceRemainsInterface(boolean checkBinary) {
        assertCompatible(compare(checkBinary, INTERFACE, INTERFACE), "A");
    }

    private static ClassInfoComparisonResults compare(boolean checkBinary, int baseAccess, int inputAccess) {
        ClassInfo baseClass = createClass("A", baseAccess);
        ClassInfo inputClass = createClass("A", inputAccess);

        ClassInfoCache baseCache = ClassInfoCache.fromMaps(ImmutableMap.of("A", baseClass), ImmutableMap.of());
        ClassInfoCache inputCache = ClassInfoCache.fromMaps(ImmutableMap.of("A", inputClass), ImmutableMap.of());
        return ClassInfoComparer.compare(checkBinary, baseCache, baseClass, inputCache, inputClass);
    }

    private static ClassInfo createClass(String name, int access) {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V1_8;
        node.access = access;
        node.name = name;
        node.superName = "java/lang/Object";
        return new ClassInfo(node);
    }
}
