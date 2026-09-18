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
import org.objectweb.asm.tree.FieldNode;

public class FieldDescriptorTests extends BaseCompatibilityTest {
    @ParameterizedTest(name = "checkBinary={0}")
    @ValueSource(booleans = {false, true})
    public void testChangedFieldDescriptor(boolean checkBinary) {
        String fieldName = "buzz";
        ClassInfo baseClass = createClassWithField("A", fieldName, "I");
        ClassInfo inputClass = createClassWithField("A", fieldName, "J");

        ClassInfoCache baseCache = ClassInfoCache.fromMaps(ImmutableMap.of("A", baseClass), ImmutableMap.of());
        ClassInfoCache inputCache = ClassInfoCache.fromMaps(ImmutableMap.of("A", inputClass), ImmutableMap.of());
        ClassInfoComparisonResults results = ClassInfoComparer.compare(checkBinary, baseCache, baseClass, inputCache, inputClass);

        assertIncompatible(results, "A", fieldName, "I", true, getExpectedMessage(checkBinary));
    }

    @ParameterizedTest(name = "checkBinary={0}")
    @ValueSource(booleans = {false, true})
    public void testDifferentDescriptorDoesNotHideMatchingParentField(boolean checkBinary) {
        String fieldName = "buzz";
        ClassInfo baseParent = createClassWithField("Parent", fieldName, "I");
        ClassInfo baseClass = createClassWithField("A", "Parent", fieldName, "I");
        ClassInfo inputParent = createClassWithField("Parent", fieldName, "I");
        ClassInfo inputClass = createClassWithField("A", "Parent", fieldName, "J");

        ClassInfoCache baseCache = ClassInfoCache.fromMaps(ImmutableMap.of("Parent", baseParent, "A", baseClass), ImmutableMap.of());
        ClassInfoCache inputCache = ClassInfoCache.fromMaps(ImmutableMap.of("Parent", inputParent, "A", inputClass), ImmutableMap.of());
        ClassInfoComparisonResults results = ClassInfoComparer.compare(checkBinary, baseCache, baseClass, inputCache, inputClass);

        assertCompatible(results, "A");
    }

    private static String getExpectedMessage(boolean checkBinary) {
        return checkBinary ? IncompatibilityMessages.FIELD_REMOVED : IncompatibilityMessages.API_FIELD_REMOVED;
    }

    private static ClassInfo createClassWithField(String name, String fieldName, String fieldDesc) {
        return createClassWithField(name, "java/lang/Object", fieldName, fieldDesc);
    }

    private static ClassInfo createClassWithField(String name, String superName, String fieldName, String fieldDesc) {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = name;
        node.superName = superName;
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, fieldName, fieldDesc, null, null));
        return new ClassInfo(node);
    }
}
