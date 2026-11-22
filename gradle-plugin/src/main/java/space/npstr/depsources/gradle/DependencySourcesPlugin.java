package space.npstr.depsources.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.tasks.SourceSetContainer;

/**
 * Register the {@code unpackDependencySources} task and feed it the compile and runtime classpaths
 * of every source set.
 */
public class DependencySourcesPlugin implements Plugin<Project> {

    public static final String TASK_NAME = "unpackDependencySources";

    @Override
    public void apply(Project project) {
        var task = project.getTasks().register(TASK_NAME, UnpackDependencySources.class, t -> {
            t.setGroup("documentation");
            t.setDescription("Unpacks the sources of all dependencies so they become greppable.");
            t.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("dependency-sources"));
        });

        project.getPlugins().withType(JavaBasePlugin.class, javaBase -> {
            var sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            sourceSets.configureEach(sourceSet -> task.configure(t -> {
                var configurations = project.getConfigurations();
                t.from(configurations.getByName(sourceSet.getCompileClasspathConfigurationName()));
                t.from(configurations.getByName(sourceSet.getRuntimeClasspathConfigurationName()));
            }));
        });
    }
}
