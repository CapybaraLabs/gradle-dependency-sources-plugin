package dev.capybaralabs.depsources.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DependencySourcesPluginTest {

    private static final String TASK = ":unpackDependencySources";

    @TempDir
    Path projectDir;

    private Path sources;

    @BeforeEach
    void setUp() throws IOException {
        sources = projectDir.resolve("build/dependency-sources");
        Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name = 'sample'\n");
        publishModuleWithoutSources();
    }

    @Test
    void unpacksSourcesOfDirectAndTransitiveDependenciesOfAllSourceSets() throws IOException {
        writeBuild("""
            implementation 'org.apache.commons:commons-lang3:3.22.0'
            testImplementation 'com.google.guava:guava:33.8.0-jre'
            implementation 'com.example:nosources:1.0'
            """);

        BuildResult result = run(TASK, "--info");

        assertThat(sources.resolve("org.apache.commons/commons-lang3/3.22.0/org/apache/commons/lang3/StringUtils.java"))
            .content().contains("public class StringUtils");
        assertThat(sources.resolve("com.google.guava/guava/33.8.0-jre/com/google/common/collect/ImmutableList.java"))
            .exists();
        // transitive dependency of guava
        assertThat(sources.resolve("com.google.guava/failureaccess")).isDirectory();
        assertThat(sources.resolve("com.example")).doesNotExist();
        assertThat(result.getOutput())
            .contains("dependencies have no published sources")
            .contains("no sources: com.example/nosources/1.0");
    }

    @Test
    void reusesConfigurationCacheAndIsUpToDateOnSecondRun() throws IOException {
        writeBuild("implementation 'org.apache.commons:commons-lang3:3.22.0'");

        run(TASK, "--configuration-cache");
        BuildResult second = run(TASK, "--configuration-cache");

        assertThat(second.getOutput()).contains("Reusing configuration cache.");
        assertThat(second.task(TASK).getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);
    }

    @Test
    void removesSourcesOfDependenciesThatWereDropped() throws IOException {
        writeBuild("""
            implementation 'org.apache.commons:commons-lang3:3.22.0'
            implementation 'org.apache.commons:commons-text:1.15.0'
            """);
        run(TASK);
        assertThat(sources.resolve("org.apache.commons/commons-text")).isDirectory();

        writeBuild("implementation 'org.apache.commons:commons-lang3:3.22.0'");
        run(TASK);

        assertThat(sources.resolve("org.apache.commons/commons-text")).doesNotExist();
        assertThat(sources.resolve("org.apache.commons/commons-lang3/3.22.0")).isDirectory();
    }

    private void writeBuild(String dependencies) throws IOException {
        Files.writeString(projectDir.resolve("build.gradle"), """
            plugins {
                id 'java'
                id 'dev.capybaralabs.dependency-sources'
            }
            repositories {
                mavenCentral()
                maven { url = file('repo') }
            }
            dependencies {
            %s
            }
            """.formatted(dependencies));
    }

    private BuildResult run(String... args) {
        return GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(args)
            .forwardOutput()
            .build();
    }

    /**
     * Publish {@code com.example:nosources:1.0} into a local Maven repository, without a sources jar.
     */
    private void publishModuleWithoutSources() throws IOException {
        Path dir = Files.createDirectories(projectDir.resolve("repo/com/example/nosources/1.0"));
        Files.writeString(dir.resolve("nosources-1.0.pom"), """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>nosources</artifactId>
              <version>1.0</version>
            </project>
            """);
        try (OutputStream out = Files.newOutputStream(dir.resolve("nosources-1.0.jar"));
             var jar = new JarOutputStream(out)) {
            jar.finish();
        }
    }
}
