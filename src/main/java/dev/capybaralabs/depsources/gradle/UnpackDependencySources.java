package dev.capybaralabs.depsources.gradle;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Unpack the sources jar of every external dependency into
 * {@code <outputDirectory>/<group>/<module>/<version>/}.
 */
@DisableCachingByDefault(because = "Unpacking is faster than downloading from a build cache")
public abstract class UnpackDependencySources extends DefaultTask {

    private final ConfigurableFileCollection sourceJarFiles;

    @Inject
    public UnpackDependencySources(ObjectFactory objects) {
        this.sourceJarFiles = objects.fileCollection().from(getSourceJars().map(Map::values));
    }

    /**
     * Coordinates ({@code group/module/version}) of the dependencies that published sources, mapped to their sources jar.
     */
    @Internal
    public abstract MapProperty<String, File> getSourceJars();

    /**
     * Coordinates of all external dependencies, with or without published sources.
     */
    @Input
    public abstract SetProperty<String> getAllCoordinates();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Input
    public Provider<Set<String>> getSourceCoordinates() {
        return getSourceJars().map(jars -> new TreeSet<>(jars.keySet()));
    }

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public ConfigurableFileCollection getSourceJarFiles() {
        return sourceJarFiles;
    }

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @Inject
    protected abstract ArchiveOperations getArchiveOperations();

    /**
     * Add the sources of all external dependencies of the given configuration.
     */
    public void from(Configuration configuration) {
        Provider<List<String>> coordinates = configuration.getIncoming()
            .artifactView(view -> view.componentFilter(UnpackDependencySources::isExternal))
            .getArtifacts().getResolvedArtifacts()
            .map(artifacts -> artifacts.stream().map(a -> coordinates(a.getId().getComponentIdentifier())).distinct().toList());
        getAllCoordinates().addAll(coordinates);

        // Ask for the sources classifier directly instead of a sources variant: many libraries publish
        // Gradle module metadata without declaring their sources jar as a variant.
        var dependencies = getProject().getDependencies();
        Configuration sources = getProject().getConfigurations().detachedConfiguration();
        sources.setTransitive(false);
        sources.getDependencies().addAllLater(coordinates.map(all -> all.stream()
            .map(c -> dependencies.create(c.replace('/', ':') + ":sources@jar"))
            .toList()));
        getSourceJars().putAll(sources.getIncoming()
            .artifactView(view -> view.setLenient(true))
            .getArtifacts().getResolvedArtifacts()
            .map(UnpackDependencySources::byCoordinates));
    }

    @TaskAction
    public void unpack() {
        var outputDirectory = getOutputDirectory().get();
        getFileSystemOperations().delete(spec -> spec.delete(outputDirectory));

        Map<String, File> sourceJars = new TreeMap<>(getSourceJars().get());
        sourceJars.forEach((coordinates, jar) -> getFileSystemOperations().copy(spec -> spec
            .from(getArchiveOperations().zipTree(jar))
            .into(outputDirectory.dir(coordinates))));

        List<String> missing = getAllCoordinates().get().stream()
            .filter(coordinates -> !sourceJars.containsKey(coordinates))
            .sorted()
            .toList();
        getLogger().lifecycle("Unpacked sources of {} dependencies into {}", sourceJars.size(), outputDirectory.getAsFile());
        if (!missing.isEmpty()) {
            getLogger().lifecycle("{} dependencies have no published sources (run with --info to list them)", missing.size());
            missing.forEach(coordinates -> getLogger().info("  no sources: {}", coordinates));
        }
    }

    private static boolean isExternal(ComponentIdentifier id) {
        return id instanceof ModuleComponentIdentifier;
    }

    private static String coordinates(ComponentIdentifier id) {
        var module = (ModuleComponentIdentifier) id;
        return module.getGroup() + "/" + module.getModule() + "/" + module.getVersion();
    }

    private static Map<String, File> byCoordinates(Set<ResolvedArtifactResult> artifacts) {
        Map<String, File> result = new TreeMap<>();
        for (var artifact : artifacts) {
            result.putIfAbsent(coordinates(artifact.getId().getComponentIdentifier()), artifact.getFile());
        }
        return result;
    }
}
