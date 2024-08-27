package net.neoforged.jarcompatibilitychecker.gradle

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import net.neoforged.jarcompatibilitychecker.ConsoleTool
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault

import javax.inject.Inject
import java.util.function.Predicate

@CompileStatic
@DisableCachingByDefault(because = 'Compatibility checks should not be cached')
abstract class CompatibilityTask extends DefaultTask {
    @Inject
    CompatibilityTask(ProjectLayout layout, ProviderFactory objects, Provider<String> version) {
        final taskDir = layout.buildDirectory.dir(name)
        output.convention(taskDir.map { it.file('output.json') })
        outputs.upToDateWhen { false }
        baseJar.set(JCCPlugin.provideLastVersion(
                taskDir.map { it.file('input.jar') }, objects, mavens, artifact, classifier,
                getVersionComponentTest().orElse(VersionComponentTest.MAJOR)
                    .flatMap { t -> version.<Predicate<String>>flatMap { ver -> t.predicate(ver) }}
        ))
        isAPI.convention(true)
        getMavens().convention(['https://maven.neoforged.net/releases', 'https://repo1.maven.org/maven2'])
    }

    @Optional
    @OutputFile
    abstract RegularFileProperty getOutput()

    @Input
    @Optional
    abstract ListProperty<String> getMavens()

    @Input
    @Optional
    abstract Property<String> getArtifact()

    @Input
    @Optional
    abstract Property<String> getClassifier()

    @InputFile
    abstract RegularFileProperty getInputJar()
    @InputFile
    abstract RegularFileProperty getBaseJar()

    @Input
    @Optional
    abstract Property<Boolean> getIsBinary()

    @Input
    @Optional
    abstract Property<Boolean> getIsAPI()

    @InputFiles
    abstract ConfigurableFileCollection getLibraries()

    @Input
    @Optional
    abstract Property<Boolean> getFail()

    @Optional
    @OutputFile
    abstract RegularFileProperty getGlobalOutput()

    @Input
    @Optional
    abstract Property<String> getProjectName()

    @Input
    @Optional
    abstract Property<VersionComponentTest> getVersionComponentTest()

    @TaskAction
    void exec() {
        final List<String> inputs = []
        inputs.add('--base-jar')
        inputs.add(getBaseJar().get().asFile.absolutePath)

        inputs.add('--input-jar')
        inputs.add(getInputJar().get().asFile.absolutePath)

        if (isBinary.isPresent() && isBinary.get()) {
            inputs.add('--binary')
        } else if (isAPI.get()) {
            inputs.add('--api')
        }

        if (output.isPresent()) {
            inputs.add('--output')
            inputs.add(output.asFile.get().absolutePath)
        }

        libraries.forEach {
            inputs.add('--lib')
            inputs.add(it.absolutePath)
        }

        if (fail.isPresent() && fail.get()) {
            inputs.add('--fail')
        }

        ConsoleTool.main(inputs as String[])

        if (output.isPresent() && globalOutput.isPresent()) {
            final output = this.output.get().asFile
            if (output.exists()) {
                final globalOutput = this.globalOutput.get().asFile
                final global = globalOutput.exists() ? new HashMap(new JsonSlurper().parse(globalOutput) as Map) : [:]
                final projectIncompats = new JsonSlurper().parse(output)
                global.put(projectName.get(), projectIncompats)
                globalOutput.write(new JsonBuilder(global).toString())
            }
        }
    }

    static enum VersionComponentTest {
        MAJOR,
        MINOR;

        Predicate<String> predicate(String currentVersion) {
            return (String targetVersion) -> {
                var splitIn = currentVersion.split('\\.', 3)
                var splitTarget = targetVersion.split('\\.', 3)
                if (this === MAJOR) {
                    return splitIn[0] == splitTarget[0]
                }
                return splitIn[0] == splitTarget[0] && splitIn[1] == splitTarget[1]
            }
        }
    }
}
