# dependency-sources

Gradle and Maven plugins that unpack the sources jars of all your dependencies (direct and transitive, main and test)
into plain directories, so that `grep`, `rg` and AI coding agents can read the code you depend on.

```
build/dependency-sources/            (Gradle)
target/dependency-sources/           (Maven)
└── <group>/<artifact>/<version>/
    └── org/example/Foo.java
```

Each run replaces the directory's contents, so sources of dependencies you dropped disappear.
Dependencies without a published sources jar are skipped and counted in the output; run with `--info` (Gradle)
or `-X` (Maven) to list them.

## Gradle

```groovy
plugins {
    id 'space.npstr.dependency-sources' version '0.1.0-SNAPSHOT'
}
```

```shell
./gradlew unpackDependencySources
```

The task collects the compile and runtime classpaths of every source set. Project dependencies are skipped, their
sources are already in your repository. Add other configurations or change the output directory on the task:

```groovy
tasks.named('unpackDependencySources') {
    from(configurations.annotationProcessor)
    outputDirectory = layout.projectDirectory.dir('.dependency-sources')
}
```

Tested with Gradle 9.7, configuration cache compatible.

The plugin is not published yet. Until then, include it as a composite build in `settings.gradle`:

```groovy
pluginManagement {
    includeBuild('../dependency-sources/gradle-plugin')
}
```

## Maven

```shell
./mvnw space.npstr:dependency-sources-maven-plugin:0.1.0-SNAPSHOT:unpack
```

Run `./mvnw install` in `maven-plugin/` first while the plugin is not published. Declare it in your `pom.xml` to shorten
the call to `./mvnw dependency-sources:unpack`:

```xml
<plugin>
    <groupId>space.npstr</groupId>
    <artifactId>dependency-sources-maven-plugin</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</plugin>
```

It only collects the dependency graph and does not need your modules to be built, so it works in multi-module projects
without running `package` first. Reactor modules are skipped. Change the output directory with
`-DdependencySources.outputDirectory=...`.

## Telling your agent

`build/` and `target/` are usually gitignored, and ripgrep (which Claude Code and many other agents search with) skips
gitignored paths when searching from the repository root. Searching the directory explicitly works fine, so tell the
agent it exists, e.g. in `CLAUDE.md` or `AGENTS.md`:

```markdown
The sources of all dependencies are unpacked in `build/dependency-sources/<group>/<artifact>/<version>/`
(run `./gradlew unpackDependencySources` to refresh). Grep there to check how a library behaves instead of guessing.
```

## Development

```shell
cd gradle-plugin && ./gradlew check   # TestKit functional tests against Maven Central
cd maven-plugin && ./mvnw verify      # maven-invoker-plugin integration tests
```
