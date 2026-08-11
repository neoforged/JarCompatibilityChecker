# JarCompatibilityChecker
**JarCompatibilityChecker** (or **JCC** for short) is a tool written with Java 8 which reports API or binary incompatibilities between two JARs.
The JAR with the API or base code to be compared against is called the **base JAR**.
The input JAR to compare against the API or base for incompatibilities is called the **concrete JAR**.

Compatibility modes:
- **API** - Checks for compatibility between the public and protected members (API) of the base JAR and concrete JAR
- **Binary** - Checks for binary compatibility between all members, both public and private, of the base JAR and concrete JAR

Additional API policy flags:
- `--non-extendable-api-check-mode SKIP|WARN|ERROR` and `--non-extendable-api-annotation <annotation>` - Controls extension-only compatibility checks for non-extendable API.
  By default, `org.jetbrains.annotations.ApiStatus$NonExtendable` is treated as a non-extendable API marker and extension-only incompatibilities are warnings.
  Non-extendable API means "public to use, unsupported to implement or subclass." This does not relax binary compatibility checks.
- `--internal-annotation-check-mode WARN|SKIP|ERROR` and `--internal-annotation <annotation>` - Controls the separate internal API policy.
  By default, `org.jetbrains.annotations.ApiStatus$Internal` is treated as an internal API marker and internal incompatibilities are warnings.
  This is separate from `@ApiStatus.NonExtendable`: internal API means "not public API", while non-extendable API means "public to use, unsupported to implement or subclass."

It is not a goal of this tool to check for source compatibility.
Because it operates on compiled class files located inside JARs, the tool only looks for incompatibilities that could cause exceptions and crashes at runtime.
This means that there may be changes between the base JAR and concrete JAR which cause a source incompatibility but are not reported by this tool as it is still compatible at runtime.

## Usage
```groovy
repositories {
    maven {
        url = 'https://maven.neoforged.net/releases'
    }
}

dependencies {
    implementation 'net.neoforged:jarcompatibilitychecker:0.1.+'
}
```

For command-line usage, the `all` classifier JAR can be downloaded and used.
For usage from inside other libraries, the no-classifier JAR can be referenced through gradle.
The main entrypoint for other libraries is the `net.neoforged.jarcompatibilitychecker.JarCompatibilityChecker` class.

## Note on Terminology
JarCompatibilityChecker and the Java Language Specification have different meanings for binary compatibility.
For JarCompatibilityChecker, binary compatibility means that all members, both public and private, are compatible between the base JAR and concrete JAR.
Compatible means that a member still exists in the concrete JAR, its type or parameters has not been changed, and its visibility has not been lowered.
For the Java Language Specification, binary compatibility means a new version of a JAR does not break other binaries depending on previous versions of that JAR.
Binaries can normally only reference public and protected members of another JAR,
so this definition of binary compatibility is more in line with JarCompatibilityChecker's definition of API compatibility.

## Gradle Plugin
A Gradle plugin with the ID `net.neoforged.jarcompatibilitychecker` is also provided.  
This Gradle plugin registers a `checkJarCompatibility` task that outputs the api/binary (configurable) changes since the last release
(determined through the base commit of the PR in a GitHub action run, otherwise the latest version, and a list of known repositories to pull the artifact from).  
The plugin is intended to be used alongside the [Jar Compatibility action](https://github.com/neoforged/action-jar-compatibility).

The Gradle task uses non-extendable API compatibility by default, and can customize the mode or marker annotations:

```groovy
tasks.named('checkJarCompatibility') {
    nonExtendableApiCheckMode.set(net.neoforged.jarcompatibilitychecker.core.NonExtendableApiCheckMode.SKIP)
    nonExtendableApiAnnotations.set([
        'org.jetbrains.annotations.ApiStatus$NonExtendable'
    ])
}
```
