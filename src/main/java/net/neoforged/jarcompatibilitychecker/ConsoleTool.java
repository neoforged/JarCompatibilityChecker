/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.jarcompatibilitychecker;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.util.EnumConverter;
import net.neoforged.jarcompatibilitychecker.core.AnnotationCheckMode;
import net.neoforged.jarcompatibilitychecker.core.ClassInfoComparisonResults;
import net.neoforged.jarcompatibilitychecker.core.InternalAnnotationCheckMode;
import net.neoforged.jarcompatibilitychecker.json.JCCJsonEncoder;

import java.io.File;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConsoleTool {
    public static void main(String[] args) {
        try {
            OptionParser parser = new OptionParser();
            OptionSpec<Void> apiO = parser.accepts("api", "Enables the API compatibility checking mode");
            OptionSpec<Void> binaryO = parser.accepts("binary", "Enables the binary compatibility checking mode. This option will override the API compatibility flag. Defaults to true.");
            OptionSpec<File> baseJarO = parser.accepts("base-jar", "Base JAR file that will be matched against for compatibility").withRequiredArg().ofType(File.class).required();
            OptionSpec<File> inputJarO = parser.accepts("input-jar", "JAR file to validate against the base JAR").withRequiredArg().ofType(File.class).required();
            OptionSpec<File> libO = parser.acceptsAll(ImmutableList.of("lib", "library"), "Libraries that the base JAR and input JAR both use").withRequiredArg().ofType(File.class);
            OptionSpec<File> baseLibO = parser.acceptsAll(ImmutableList.of("base-lib", "base-library"), "Libraries that only the base JAR uses").withRequiredArg().ofType(File.class);
            OptionSpec<File> concreteLibO = parser.acceptsAll(ImmutableList.of("concrete-lib", "concrete-library"), "Libraries that only the input JAR uses").withRequiredArg().ofType(File.class);
            OptionSpec<AnnotationCheckMode> annotationCheckModeO = parser.acceptsAll(ImmutableList.of("annotation-check-mode", "ann-mode"), "What mode to use for checking annotations")
                    .withRequiredArg().withValuesConvertedBy(new EnumConverter<AnnotationCheckMode>(AnnotationCheckMode.class) {});
            OptionSpec<String> internalAnnotationO = parser.acceptsAll(ImmutableList.of("internal-annotation", "internal-ann"), "The fully resolved classname of an allowed internal API annotation")
                    .withRequiredArg().defaultsTo(InternalAnnotationCheckMode.DEFAULT_INTERNAL_ANNOTATIONS.toArray(new String[0]));
            OptionSpec<InternalAnnotationCheckMode> internalAnnotationCheckModeO = parser.acceptsAll(
                    ImmutableList.of("internal-annotation-check-mode", "internal-ann-mode"),
                    "What mode to use for checking elements marked with an internal API annotation"
            ).withRequiredArg().withValuesConvertedBy(new EnumConverter<InternalAnnotationCheckMode>(InternalAnnotationCheckMode.class) {}).defaultsTo(InternalAnnotationCheckMode.DEFAULT_MODE);
            OptionSpec<Void> failO = parser.accepts("fail", "If enabled, the tool will fail if incompatibilities are detected");
            OptionSpec<File> outputO = parser.accepts("output", "A file to which the incompatibilities should be dumped to").withRequiredArg().ofType(File.class);

            OptionSet options;
            try {
                options = parser.parse(args);
            } catch (OptionException ex) {
                System.err.println("Error: " + ex.getMessage());
                System.err.println();
                parser.printHelpOn(System.err);
                System.exit(-1);
                return;
            }

            File baseJar = options.valueOf(baseJarO);
            File inputJar = options.valueOf(inputJarO);
            List<File> commonLibs = options.valuesOf(libO);
            List<File> baseLibs = options.valuesOf(baseLibO);
            List<File> concreteLibs = options.valuesOf(concreteLibO);
            boolean checkBinary = !options.has(apiO) || options.has(binaryO);
            AnnotationCheckMode annotationCheckMode = options.valueOf(annotationCheckModeO);
            List<String> internalAnnotations = options.valuesOf(internalAnnotationO);
            InternalAnnotationCheckMode internalAnnotationCheckMode = options.valueOf(internalAnnotationCheckModeO);

            // TODO allow logging to a file
            JarCompatibilityChecker checker = new JarCompatibilityChecker(baseJar, inputJar, checkBinary, annotationCheckMode, internalAnnotations, internalAnnotationCheckMode,
                    commonLibs, baseLibs, concreteLibs, System.out::println, System.err::println);

            Map.Entry<Integer, List<ClassInfoComparisonResults>> incompatibilities = checker.check();

            if (options.has(outputO)) {
                final Map<String, JCCJsonEncoder.Incompat> incompats = new HashMap<>();
                incompatibilities.getValue().forEach(result -> result.getIncompatibilities().forEach(incompat -> JCCJsonEncoder.accept(incompats, incompat)));
                final Path output = options.valueOf(outputO).toPath();
                if (output.getParent() != null) java.nio.file.Files.createDirectories(output.getParent());
                try (final Writer writer = java.nio.file.Files.newBufferedWriter(output)) {
                    new GsonBuilder().registerTypeAdapter(ListMultimap.class, (JsonSerializer<ListMultimap<?, ?>>) (src, typeOfSrc, context) -> context.serialize(src.asMap())).create().toJson(incompats, writer);
                }
            }

            if (options.has(failO)) {
                // Clamp to a max of 125 to prevent conflicting with special meaning exit codes - https://tldp.org/LDP/abs/html/exitcodes.html
                System.exit(Math.min(125, incompatibilities.getKey()));
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
}
