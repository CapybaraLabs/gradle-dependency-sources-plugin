# dependency-sources

A Gradle plugin that unpacks the sources jars of all your dependencies (direct and transitive, main and test) into
plain directories, so that `grep`, `rg` and AI coding agents can read the code you depend on.

```
build/dependency-sources/
└── <group>/<artifact>/<version>/
    └── org/example/Foo.java
```

Each run replaces the directory's contents, so sources of dependencies you dropped disappear.
Dependencies without a published sources jar are skipped and counted in the output; run with `--info` to list them.

## Usage

```groovy
plugins {
    id 'dev.capybaralabs.dependency-sources' version '0.1.0-SNAPSHOT'
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
    includeBuild('../dependency-sources')
}
```

## Maven

No plugin needed, the stock dependency plugin does the job:

```shell
rm -rf target/dependency-sources && ./mvnw compile dependency:unpack-dependencies -Dclassifier=sources \
  -Dmdep.useSubDirectoryPerArtifact=true -DoutputDirectory=target/dependency-sources
```

- `rm -rf` removes sources of dependencies you dropped since the last run.
- `compile` lets multi-module builds resolve their sibling modules; without it the goal fails on them.
- Missing sources jars are skipped silently, and folders are named `<artifact>-<version>-sources-jar`, without the group.

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
./gradlew check   # TestKit functional tests against Maven Central
```
