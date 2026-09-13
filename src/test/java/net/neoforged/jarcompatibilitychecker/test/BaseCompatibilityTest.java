/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.jarcompatibilitychecker.test;

import net.neoforged.jarcompatibilitychecker.core.ClassInfoCache;
import net.neoforged.jarcompatibilitychecker.core.ClassInfoComparer;
import net.neoforged.jarcompatibilitychecker.core.ClassInfoComparisonResults;
import net.neoforged.jarcompatibilitychecker.core.Incompatibility;
import net.neoforged.jarcompatibilitychecker.data.ClassInfo;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public abstract class BaseCompatibilityTest {
    protected Path getRoot() {
        URL url = this.getClass().getResource("/test.marker");
        Assertions.assertNotNull(url, "Could not find test.marker");

        try {
            return new File(url.toURI()).getParentFile().toPath();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    protected void assertIncompatible(boolean checkBinary, String folder, String className, IncompatibilityData... testIncompatibilities) {
        if (testIncompatibilities.length == 0)
            throw new IllegalArgumentException("Must provide at least one incompatibility to test");

        ClassInfoComparisonResults comparisonResults = getComparisonResults(checkBinary, folder, className);
        assertIncompatible(comparisonResults, className, testIncompatibilities);
    }

    protected void assertIncompatible(ClassInfoComparisonResults comparisonResults, String className, IncompatibilityData... testIncompatibilities) {
        if (testIncompatibilities.length == 0)
            throw new IllegalArgumentException("Must provide at least one incompatibility to test");

        Assertions.assertFalse(comparisonResults.isCompatible(), className + " was compatible when incompatibilities were expected");

        List<Incompatibility<?>> incompatibilities = comparisonResults.getIncompatibilities();
        Assertions.assertEquals(testIncompatibilities.length, incompatibilities.size(), className + " had the wrong number of incompatibilities: " + comparisonResults);

        for (int i = 0; i < testIncompatibilities.length; i++) {
            IncompatibilityData testData = testIncompatibilities[i];
            Incompatibility<?> incompatibility = incompatibilities.get(i);
            Assertions.assertEquals(testData.getName(), incompatibility.getInfo().getName(), className + " had an incompatibility with the wrong name: " + incompatibility);
            Assertions.assertEquals(testData.getDesc(), incompatibility.getInfo().getDescriptor(), className + " had an incompatibility with the wrong descriptor: " + incompatibility);
            Assertions.assertEquals(testData.getMessage(), incompatibility.getMessage(), className + " had an incompatibility with the wrong message: " + incompatibility);
            Assertions.assertEquals(testData.isError(), incompatibility.isError(), className + " had an incompatibility with mismatch error vs. warning: " + incompatibility);
        }
    }

    protected void assertClassIncompatible(boolean checkBinary, String folder, String className, String message, Object... formatArgs) {
        assertClassIncompatible(checkBinary, folder, className, true, message, formatArgs);
    }

    protected void assertClassIncompatible(ClassInfoComparisonResults comparisonResults, String className, String message, Object... formatArgs) {
        assertClassIncompatible(comparisonResults, className, true, message, formatArgs);
    }

    protected void assertClassIncompatible(boolean checkBinary, String folder, String className, boolean isError, String message, Object... formatArgs) {
        assertIncompatible(checkBinary, folder, className, className, null, isError, message, formatArgs);
    }

    protected void assertClassIncompatible(ClassInfoComparisonResults comparisonResults, String className, boolean isError, String message, Object... formatArgs) {
        assertIncompatible(comparisonResults, className, className, null, isError, message, formatArgs);
    }

    protected void assertIncompatible(boolean checkBinary, String folder, String className, String name, @Nullable String desc, String message, Object... formatArgs) {
        assertIncompatible(checkBinary, folder, className, name, desc, true, message, formatArgs);
    }

    protected void assertIncompatible(boolean checkBinary, String folder, String className, String name, @Nullable String desc, boolean isError, String message, Object... formatArgs) {
        if (formatArgs.length > 0)
            message = String.format(Locale.ROOT, message, formatArgs);
        ClassInfoComparisonResults comparisonResults = getComparisonResults(checkBinary, folder, className);
        assertIncompatible(comparisonResults, className, name, desc, isError, message);
    }

    protected void assertIncompatible(ClassInfoComparisonResults comparisonResults, String className, String name, @Nullable String desc, boolean isError, String message,
            Object... formatArgs) {
        if (formatArgs.length > 0)
            message = String.format(Locale.ROOT, message, formatArgs);
        Assertions.assertFalse(comparisonResults.isCompatible(), className + " was compatible when incompatibilities were expected");

        List<Incompatibility<?>> incompatibilities = comparisonResults.getIncompatibilities();
        Assertions.assertEquals(1, incompatibilities.size(), className + " had more than one incompatibility when one was expected: " + comparisonResults);

        Incompatibility<?> incompatibility = incompatibilities.get(0);
        Assertions.assertEquals(name, incompatibility.getInfo().getName(), className + " had an incompatibility with the wrong name: " + incompatibility);
        Assertions.assertEquals(desc, incompatibility.getInfo().getDescriptor(), className + " had an incompatibility with the wrong descriptor: " + incompatibility);
        Assertions.assertEquals(message, incompatibility.getMessage(), className + " had an incompatibility with the wrong message: " + incompatibility);
        Assertions.assertEquals(isError, incompatibility.isError(), className + " had an incompatibility with mismatch error vs. warning: " + incompatibility);
    }

    protected void assertCompatible(boolean checkBinary, String folder, String className) {
        ClassInfoComparisonResults comparisonResults = getComparisonResults(checkBinary, folder, className);
        assertCompatible(comparisonResults, className);
    }

    protected void assertCompatible(ClassInfoComparisonResults comparisonResults, String className) {
        Assertions.assertTrue(comparisonResults.isCompatible(), className + " had incompatibilities when none were expected: " + comparisonResults);
    }

    protected ClassInfoComparisonResults getComparisonResults(boolean checkBinary, String folderName, String className) {
        return getComparisonResults(folderName, className, (baseCache, baseClassInfo, inputCache, inputClassInfo) ->
                ClassInfoComparer.compare(checkBinary, baseCache, baseClassInfo, inputCache, inputClassInfo));
    }

    protected ClassInfoComparisonResults getComparisonResults(String folderName, String className, Comparison comparison) {
        try {
            Path folder = getRoot().resolve(folderName);
            if (!folder.toRealPath().toString().equals(folder.toAbsolutePath().toString()))
                throw new IllegalArgumentException("Folder \"" + folderName + "\" does not match the real path \"" + folder.toRealPath().getFileName().toString() + "\"");

            Path baseFolder = folder.resolve("base");
            Assertions.assertTrue(Files.exists(baseFolder), baseFolder + " not found");
            Assertions.assertEquals(baseFolder.toAbsolutePath(), baseFolder.toRealPath(), "Base folder in " + folderName + " has invalid casing");

            Path inputFolder = folder.resolve("input");
            boolean inputExists = Files.exists(inputFolder); // If it doesn't exist, all base classes got deleted, which is technically valid.
            if (inputExists)
                Assertions.assertEquals(inputFolder.toAbsolutePath(), inputFolder.toRealPath(), "Input folder in " + folderName + " has invalid casing");

            ClassInfoCache baseCache = ClassInfoCache.fromFolder(baseFolder);
            ClassInfoCache inputCache = inputExists ? ClassInfoCache.fromFolder(inputFolder) : ClassInfoCache.empty();

            ClassInfo baseClassInfo = baseCache.getMainClassInfo(className);
            Assertions.assertNotNull(baseClassInfo, "Class with name " + className + " not found in " + baseFolder);

            return comparison.compare(baseCache, baseClassInfo, inputCache, inputCache.getMainClassInfo(className));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    protected interface Comparison {
        ClassInfoComparisonResults compare(ClassInfoCache baseCache, ClassInfo baseClassInfo, ClassInfoCache inputCache, @Nullable ClassInfo inputClassInfo);
    }

    protected static class IncompatibilityData {
        private final String name;
        @Nullable
        private final String desc;
        private final String message;
        private final boolean isError;

        protected IncompatibilityData(String name, @Nullable String desc, String message, Object... formatArgs) {
            this(name, desc, true, message, formatArgs);
        }

        protected IncompatibilityData(String name, @Nullable String desc, boolean isError, String message, Object... formatArgs) {
            this.name = name;
            this.desc = desc;
            this.isError = isError;
            this.message = formatArgs.length > 0 ? String.format(Locale.ROOT, message, formatArgs) : message;
        }

        protected String getName() {
            return this.name;
        }

        @Nullable
        protected String getDesc() {
            return this.desc;
        }

        protected String getMessage() {
            return this.message;
        }

        protected boolean isError() {
            return this.isError;
        }
    }
}
