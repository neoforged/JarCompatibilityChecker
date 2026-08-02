package net.neoforged.jarcompatibilitychecker.json;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.gson.Gson;
import net.neoforged.jarcompatibilitychecker.core.AnnotationIncompatibility;
import net.neoforged.jarcompatibilitychecker.core.ClassIncompatibility;
import net.neoforged.jarcompatibilitychecker.core.FieldIncompatibility;
import net.neoforged.jarcompatibilitychecker.core.Incompatibility;
import net.neoforged.jarcompatibilitychecker.core.MethodIncompatibility;
import net.neoforged.jarcompatibilitychecker.data.ClassInfo;
import net.neoforged.jarcompatibilitychecker.data.FieldInfo;
import net.neoforged.jarcompatibilitychecker.data.MemberInfo;
import net.neoforged.jarcompatibilitychecker.data.MethodInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JCCJsonEncoder {
    public static void accept(Map<String, Incompat> incompats, Incompatibility<?> incompat) {
        if (incompat instanceof ClassIncompatibility) {
            final ClassIncompatibility ci = (ClassIncompatibility) incompat;
            incompats.computeIfAbsent(ci.getInfo().getName(), k -> new Incompat()).classIncompatibilities.add(new Incompat.BaseIncompat(ci.getMessage(), ci.isError()));
        } else if (incompat instanceof FieldIncompatibility) {
            final FieldIncompatibility fi = (FieldIncompatibility) incompat;
            incompats.computeIfAbsent(fi.getInfo().parent.getName(), k -> new Incompat()).fieldIncompatibilities.put(fi.getInfo().getName() + ":" + fi.getInfo().getDescriptor(), new Incompat.BaseIncompat(fi.getMessage(), fi.isError()));
        } else if (incompat instanceof MethodIncompatibility) {
            final MethodIncompatibility mi = (MethodIncompatibility) incompat;
            incompats.computeIfAbsent(mi.getInfo().parent.getName(), k -> new Incompat()).methodIncompatibilities.put(mi.getInfo().getName() + mi.getInfo().getDescriptor(), new Incompat.BaseIncompat(mi.getMessage(), mi.isError()));
        } else if (incompat instanceof AnnotationIncompatibility) {
            final AnnotationIncompatibility<?> ai = (AnnotationIncompatibility<?>) incompat;
            final MemberInfo memberInfo = ai.getInfo();
            final Incompat.BaseIncompat encoded = new Incompat.BaseIncompat(ai.getAnnotationInfo() + " - " + ai.getMessage(), ai.isError());
            if (memberInfo instanceof ClassInfo) {
                incompats.computeIfAbsent(memberInfo.getName(), k -> new Incompat()).classIncompatibilities.add(encoded);
            } else if (memberInfo instanceof FieldInfo) {
                final FieldInfo fi = (FieldInfo) memberInfo;
                incompats.computeIfAbsent(fi.parent.getName(), k -> new Incompat()).fieldIncompatibilities.put(fi.getName() + ":" + fi.getDescriptor(), encoded);
            } else if (memberInfo instanceof MethodInfo) {
                final MethodInfo mi = (MethodInfo) memberInfo;
                incompats.computeIfAbsent(mi.parent.getName(), k -> new Incompat()).methodIncompatibilities.put(mi.getName() + mi.getDescriptor(), encoded);
            }
        }
    }

    public static String toJson(List<Incompatibility<?>> incompatibilities) {
        final Map<String, Incompat> incompats = new HashMap<>();
        incompatibilities.forEach(incompat -> accept(incompats, incompat));
        return new Gson().toJson(incompats);
    }

    public static class Incompat {
        final List<BaseIncompat> classIncompatibilities = new ArrayList<>();
        final ListMultimap<String, BaseIncompat> methodIncompatibilities = Multimaps.newListMultimap(new HashMap<>(), ArrayList::new);
        final ListMultimap<String, BaseIncompat> fieldIncompatibilities = Multimaps.newListMultimap(new HashMap<>(), ArrayList::new);

        static class BaseIncompat {
            public final String message;
            public final boolean isError;

            BaseIncompat(String message, boolean isError) {
                this.message = message;
                this.isError = isError;
            }
        }
    }
}
