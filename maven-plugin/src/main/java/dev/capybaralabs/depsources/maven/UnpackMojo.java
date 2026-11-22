package dev.capybaralabs.depsources.maven;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.inject.Inject;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * Unpack the sources jar of every dependency (including transitive and test scoped ones) into
 * {@code <outputDirectory>/<groupId>/<artifactId>/<version>/}.
 */
@Mojo(name = "unpack", requiresDependencyCollection = ResolutionScope.TEST,
    defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public class UnpackMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    /**
     * Where to unpack the sources to.
     */
    @Parameter(property = "dependencySources.outputDirectory", defaultValue = "${project.build.directory}/dependency-sources")
    private File outputDirectory;

    private final RepositorySystem repositorySystem;

    @Inject
    public UnpackMojo(RepositorySystem repositorySystem) {
        this.repositorySystem = repositorySystem;
    }

    @Override
    public void execute() throws MojoExecutionException {
        Set<String> reactorProjects = session.getProjects().stream()
            .map(p -> p.getGroupId() + ":" + p.getArtifactId())
            .collect(Collectors.toSet());

        List<ArtifactRequest> requests = new ArrayList<>();
        for (Artifact dependency : project.getArtifacts()) {
            if (reactorProjects.contains(dependency.getGroupId() + ":" + dependency.getArtifactId())) {
                continue; // its sources are right here in the reactor
            }
            var sources = new DefaultArtifact(dependency.getGroupId(), dependency.getArtifactId(), "sources", "jar", dependency.getVersion());
            requests.add(new ArtifactRequest(sources, project.getRemoteProjectRepositories(), null));
        }

        List<ArtifactResult> results;
        try {
            results = repositorySystem.resolveArtifacts(session.getRepositorySession(), requests);
        } catch (ArtifactResolutionException e) {
            results = e.getResults(); // dependencies without published sources
        }

        Path out = outputDirectory.toPath();
        Set<String> missing = new TreeSet<>();
        int unpacked = 0;
        try {
            deleteRecursively(out);
            for (ArtifactResult result : results) {
                var artifact = result.getRequest().getArtifact();
                if (!result.isResolved()) {
                    missing.add(artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getBaseVersion());
                    continue;
                }
                unzip(result.getArtifact().getFile().toPath(),
                    out.resolve(artifact.getGroupId()).resolve(artifact.getArtifactId()).resolve(artifact.getBaseVersion()));
                unpacked++;
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to unpack dependency sources into " + out, e);
        }

        getLog().info("Unpacked sources of " + unpacked + " dependencies into " + out);
        if (!missing.isEmpty()) {
            getLog().info(missing.size() + " dependencies have no published sources (run with -X to list them)");
            missing.forEach(coordinates -> getLog().debug("  no sources: " + coordinates));
        }
    }

    private static void unzip(Path zip, Path target) throws IOException {
        Path root = target.toAbsolutePath().normalize();
        try (var in = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry entry; (entry = in.getNextEntry()) != null; ) {
                Path path = root.resolve(entry.getName()).normalize();
                if (!path.startsWith(root)) {
                    throw new IOException("Zip entry " + entry.getName() + " in " + zip + " escapes the target directory");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(path);
                } else {
                    Files.createDirectories(path.getParent());
                    Files.copy((InputStream) in, path, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
