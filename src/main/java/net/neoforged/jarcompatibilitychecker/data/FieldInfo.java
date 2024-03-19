/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.jarcompatibilitychecker.data;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;

import java.lang.reflect.Field;
import java.util.List;

public class FieldInfo implements MemberInfo {
    public final String name;
    public final String desc;
    public final int access;
    public final List<AnnotationInfo> annotations;
    public final ClassInfo parent;

    public FieldInfo(FieldNode node, ClassInfo parent) {
        this.name = node.name;
        this.desc = node.desc;
        this.access = node.access;
        this.parent = parent;
        this.annotations = AnnotationInfo.create(node.visibleAnnotations, node.invisibleAnnotations);
    }

    public FieldInfo(Field node, ClassInfo parent) {
        this.name = node.getName();
        this.desc = Type.getType(node.getType()).getDescriptor();
        this.access = node.getModifiers();
        this.parent = parent;
        this.annotations = ImmutableList.of();
    }

    @NotNull
    @Override
    public String getName() {
        return this.name;
    }

    @Nullable
    @Override
    public String getDescriptor() {
        return this.desc;
    }

    @Override
    public int getAccess() {
        return this.access;
    }

    @NotNull
    @Override
    public List<AnnotationInfo> getAnnotations() {
        return this.annotations;
    }

    @Override
    public String toString() {
        return this.name + ':' + this.desc;
    }
}
