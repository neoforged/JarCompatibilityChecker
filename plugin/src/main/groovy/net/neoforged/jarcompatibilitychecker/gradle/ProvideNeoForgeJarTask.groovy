package net.neoforged.jarcompatibilitychecker.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.ProcessOperations
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.jvm.toolchain.JavaLauncher

import javax.inject.Inject
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

abstract class ProvideNeoForgeJarTask extends DefaultTask {
    public static final String BIN_PATCHER = 'https://maven.neoforged.net/releases/net/neoforged/installertools/binarypatcher/2.1.2/binarypatcher-2.1.2-fatjar.jar'

    @Inject
    ProvideNeoForgeJarTask() {
        this.output.set(layout.buildDirectory.dir(name).map { it.file('output.jar') })
    }

    @OutputFile
    abstract RegularFileProperty getOutput()

    @InputFile
    abstract RegularFileProperty getCleanJar()

    @Input
    abstract Property<String> getMaven()
    @Input
    abstract Property<String> getArtifact()
    @Input
    abstract Property<String> getVersion()

    @Inject
    abstract ProjectLayout getLayout()

    @Inject
    abstract FileOperations getFileOperations()

    @Inject
    abstract ProcessOperations getProcessOperations()

    @Nested
    abstract Property<JavaLauncher> getJavaLauncher()

    @TaskAction
    void exec() {
        final art = artifact.get().split(':', 2)
        final version = this.version.get()
        final path = { String classifier -> "${maven.get()}/${art[0].replace('.', '/')}/${art[1]}/${version}/${art[1]}-${version}-${classifier}.jar"}

        final dir = layout.buildDirectory.dir(name).get()
        final installer = dir.file('installer.jar').asFile
        JCCPlugin.download(path('installer'), installer)
        final universal = dir.file('universal.jar').asFile
        JCCPlugin.download(path('universal'), universal)

        final patches = dir.file('client.lzma').asFile
        Files.copy(fileOperations.zipTree(installer).matching { it.include('data/client.lzma') }.singleFile.toPath(), patches.toPath(), StandardCopyOption.REPLACE_EXISTING)

        final binpatcher = dir.file('binpatcher.jar').asFile
        JCCPlugin.download(BIN_PATCHER, binpatcher)

        JarFile jarFile = new JarFile(binpatcher)
        final mainClass = jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS)
        jarFile.close()

        final patchedOut = dir.file('patched.jar').asFile

        final workingDir = dir.file('binpatcherrun').asFile
        workingDir.mkdirs()
        processOperations.javaexec {
            it.args('--clean', cleanJar.get().asFile, '--output', patchedOut, '--apply', patches, '--unpatched')
            it.mainClass.set(mainClass)
            it.setClasspath(fileOperations.configurableFiles(binpatcher))
            it.setExecutable(javaLauncher.get().executablePath.toString())
            it.setWorkingDir(workingDir)
        }

        final jars = [patchedOut, universal]
        try (final zout = new ZipOutputStream(new FileOutputStream(output.get().asFile))) {
            for (final jar : jars) {
                try (final zin = new ZipInputStream(new FileInputStream(jar))) {
                    ZipEntry entry
                    while ((entry = zin.getNextEntry()) !== null) {
                        ZipEntry _new = new ZipEntry(entry.getName())
                        _new.setTime(0)
                        zout.putNextEntry(_new)
                        zout << zin
                    }
                }
            }
        }
    }
}
