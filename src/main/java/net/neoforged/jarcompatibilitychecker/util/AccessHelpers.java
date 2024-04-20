package net.neoforged.jarcompatibilitychecker.util;

import net.neoforged.jarcompatibilitychecker.data.MemberInfo;
import org.objectweb.asm.Opcodes;

public class AccessHelpers {
    public static boolean isSynthetic(int access) {
        return ((access & Opcodes.ACC_SYNTHETIC)) != 0;
    }

    public static boolean isSynthetic(MemberInfo info) {
        return isSynthetic(info.getAccess());
    }
}
