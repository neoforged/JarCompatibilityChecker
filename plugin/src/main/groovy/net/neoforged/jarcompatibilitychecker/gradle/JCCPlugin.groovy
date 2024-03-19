package net.neoforged.jarcompatibilitychecker.gradle

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.xml.XmlSlurper
import org.apache.log4j.LogManager
import org.apache.log4j.Logger
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.jvm.tasks.Jar

class JCCPlugin implements Plugin<Project> {
    static final Logger LOG = LogManager.getLogger(JCCPlugin)

    @Override
    @CompileStatic
    void apply(Project target) {
        target.plugins.withId('java') {
            final compileCp = target.configurations.named('compileClasspath')
            final group = target.provider { target.group }
            final jarTask = target.tasks.named('jar', Jar)
            final projectName = target.name + ' (' + target.path + ')'
            final globalOutputFile = target.rootProject.file('jcc.json')
            final providers = target.providers
            final ciSystemEnv = target.providers.environmentVariable('CI')
            target.tasks.register('checkJarCompatibility', CompatibilityTask, target.layout, target.providers).configure {
                it.inputJar.set(jarTask.flatMap { it.archiveFile })
                it.artifact.set(group.<String>map { Object gr -> gr.toString() + ':' + jarTask.get().archiveBaseName.get() })
                it.libraries.from(compileCp)
                it.projectName.set(projectName)
                it.globalOutput.fileProvider(providers.provider { ciSystemEnv.isPresent() ? globalOutputFile : null })
            }
        }
    }

    static Provider<String> providePreviousVersion(ProviderFactory objects, Provider<List<String>> mavens, Provider<String> artifact) {
        return objects.gradleProperty('GITHUB_EVENT_PATH')
            .map {
                final path = new File(it)
                if (path.exists()) {
                    LOG.debug("Found GitHub JSON payload at ${it}")

                    final slurper = new JsonSlurper()
                    final json = slurper.parse(new File(it))
                    final pr = json.pull_request
                    if (pr === null) {
                        LOG.debug('Payload is not associated with a PR')
                        return null
                    }

                    final baseCommit = pr.base.sha as String
                    final statusesUrl = "https://api.github.com/repos/${json.pull_request.base.repo.full_name}/statuses/${baseCommit}"
                    final conn = URI.create(statusesUrl).toURL().openConnection()
                    conn.setRequestProperty('Authorization', "Bearer ${System.getenv('GITHUB_TOKEN')}")
                    final statuses = slurper.parse(conn.getInputStream()) as List
                    final status = statuses.find {
                        (it.description as String).startsWith('Version: ')
                    }
                    if (status === null) {
                        LOG.debug("Found no status on commit ${baseCommit}")
                        return null
                    } else {
                        LOG.debug("Found status associated with action run ${pr.target_url}")
                        return (status.description as String).replace('Version: ', '')
                    }
                }

                return null
            }
            .orElse(mavens.map { mvns ->
                final art = artifact.get().split(':', 2)
                for (final maven : mvns) {
                    final url = "${maven}/${art[0].replace('.', '/')}/${art[1]}/maven-metadata.xml"
                    try {
                        final latestVersion = new XmlSlurper().parse(url).versioning?.latest?.text() as String
                        LOG.debug("Found artifact ${artifact.get()} @ maven ${maven}")
                        return latestVersion
                    } catch (Throwable ignored) {

                    }
                }
                return null
            })
    }

    static Provider<RegularFile> provideLastVersion(Provider<RegularFile> _file, ProviderFactory objects, Provider<List<String>> mavens, Provider<String> artifact, Provider<String> classifier) {
        return providePreviousVersion(objects, mavens, artifact)
            .flatMap { version ->
                _file.flatMap { file ->
                    return artifact.flatMap { art ->
                        return mavens.map { mvns ->
                            final artPath = artifact.get().split(':', 2)
                            for (final maven : mvns) {
                                final url = "${maven}/${artPath[0].replace('.', '/')}/${artPath[1]}/${version}/${artPath[1]}-${version}${classifier.isPresent() ? '-' + classifier.get() : ''}.jar"
                                if (download(url, file.asFile)) {
                                    LOG.debug("Downloaded file version ${version} from ${url}")
                                    return file
                                }
                            }
                            return null
                        }
                    }
                }
            }
    }

    static boolean download(String url, File file) {
        try {
            final uri = url.toURL().openConnection() as HttpURLConnection
            uri.connect()
            if (uri.responseCode === 404) {
                return false
            }
            uri.tap { conn ->
                new File(file.parent).mkdirs()
                file.withOutputStream { out ->
                    conn.inputStream.with { inp ->
                        out << inp
                        inp.close()
                    }
                }
            }
            return true
        } catch (Throwable ignored) {
            return false
        }
    }
}
