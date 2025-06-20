/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.maven;

import com.facebook.presto.spi.Plugin;
import com.facebook.presto.spi.CoordinatorPlugin;
import com.facebook.presto.spi.RouterPlugin;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;

/**
 * Mojo that generates the default service descriptor for Presto plugins to
 * {@code META-INF/services/com.facebook.presto.spi.Plugin} and
 * {@code META-INF/services/com.facebook.presto.spi.CoordinatorPlugin}.
 *
 * @author Jason van Zyl
 */
@Mojo(name = "generate-service-descriptor", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.COMPILE)
public class ServiceDescriptorGenerator
        extends AbstractMojo
{
    private static final String LS = System.getProperty("line.separator");
    private static final Class<?>[] PLUGIN_TYPES = new Class<?>[] {Plugin.class, CoordinatorPlugin.class, RouterPlugin.class};

    @Parameter(defaultValue = "${project.build.outputDirectory}/META-INF/services/com.facebook.presto.spi.Plugin")
    private File servicesFile;

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private File classesDirectory;

    @Parameter(defaultValue = "${project}")
    private MavenProject project;

    @Override
    public void execute()
            throws MojoExecutionException, MojoFailureException
    {
        // If users have already provided their own service file then we will not overwrite it
        if (servicesFile.exists()) {
            return;
        }

        if (!servicesFile.getParentFile().exists()) {
            mkdirs(servicesFile.getParentFile());
        }

        Map<Class<?>, List<Class<?>>> pluginClassMap = new HashMap<>();

        try {
            URLClassLoader loader = createClassloaderFromCompileTimeDependencies();
            for (Class<?> pluginType : PLUGIN_TYPES) {
                pluginClassMap.put(pluginType, findImplementationsOf(pluginType, loader));
            }
        }
        catch (Exception e) {
            throw new MojoExecutionException(format("%n%nError scanning for classes implementing %s, %s, and %s.", Plugin.class.getName(), CoordinatorPlugin.class.getName(), RouterPlugin.class.getName()), e);
        }

        if (pluginClassMap.values().stream().allMatch(List::isEmpty)) {
            throw new MojoExecutionException(format("%n%nYou must have at least one class that implements %s, %s, or %s.", Plugin.class.getName(), CoordinatorPlugin.class.getName(), RouterPlugin.class.getName()));
        }

        if (pluginClassMap.values().stream().filter(l -> !l.isEmpty()).count() > 1) {
            throw new MojoExecutionException(format("%n%nYou have classes that implement multiple of %s, %s, or %s. You can only have one plugin implementation per project.", Plugin.class.getName(), CoordinatorPlugin.class.getName(), RouterPlugin.class.getName()));
        }

        for (Class<?> pluginType : pluginClassMap.keySet()) {
            List<Class<?>> pluginTypeClasses = pluginClassMap.get(pluginType);
            if (pluginTypeClasses == null || pluginTypeClasses.isEmpty()) {
                continue;
            }
            ensureSinglePluginImplementation(pluginTypeClasses, pluginType);
            File typeServicesFile = servicesFile;
            if (pluginType.equals(CoordinatorPlugin.class) || pluginType.equals(RouterPlugin.class)) {
                typeServicesFile = new File(servicesFile.getParent() + "/" + pluginType.getName());
            }
            writeServiceDescriptor(pluginTypeClasses, pluginType, typeServicesFile);
        }
    }

    private void writeServiceDescriptor(List<Class<?>> pluginClasses, Class<?> classImplementationTemplate, File servicesFileToWrite)
            throws MojoExecutionException
    {
        try {
            Class<?> pluginClass = pluginClasses.get(0);
            Files.write(pluginClass.getName().getBytes(Charsets.UTF_8), servicesFileToWrite);
            getLog().info(format("Wrote META-INF/services/%s with %s", classImplementationTemplate.getName(), pluginClass.getName()));
        }
        catch (IOException e) {
            throw new MojoExecutionException("Failed to write service descriptor.", e);
        }
    }

    private void ensureSinglePluginImplementation(List<Class<?>> pluginClasses, Class<?> classImplementationTemplate)
            throws MojoExecutionException
    {
        if (pluginClasses.size() > 1) {
            StringBuilder sb = new StringBuilder();
            for (Class<?> pluginClass : pluginClasses) {
                sb.append(pluginClass.getName()).append(LS);
            }
            throw new MojoExecutionException(format("%n%nYou have more than one class that implements %s:%n%n%s%nYou can only have one per plugin project.", classImplementationTemplate.getName(), sb));
        }
    }

    private URLClassLoader createClassloaderFromCompileTimeDependencies()
            throws Exception
    {
        List<URL> urls = Lists.newArrayList();
        urls.add(classesDirectory.toURI().toURL());
        for (Artifact artifact : project.getArtifacts()) {
            if (artifact.getFile() != null) {
                urls.add(artifact.getFile().toURI().toURL());
            }
        }
        return new URLClassLoader(urls.toArray(new URL[urls.size()]));
    }

    private List<Class<?>> findImplementationsOf(Class<?> implementationTemplate, URLClassLoader searchRealm)
            throws IOException, MojoExecutionException
    {
        List<Class<?>> implementations = Lists.newArrayList();
        List<String> classes = FileUtils.getFileNames(classesDirectory, "**/*.class", null, false);
        for (String classPath : classes) {
            String className = classPath.substring(0, classPath.length() - 6).replace(File.separatorChar, '.');
            try {
                Class<?> implementation = searchRealm.loadClass(implementationTemplate.getName());
                Class<?> clazz = searchRealm.loadClass(className);
                if (implementation.isAssignableFrom(clazz)) {
                    implementations.add(clazz);
                }
            }
            catch (ClassNotFoundException e) {
                throw new MojoExecutionException("Failed to load class.", e);
            }
        }
        return implementations;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void mkdirs(File file)
            throws MojoExecutionException
    {
        file.mkdirs();
        if (!file.isDirectory()) {
            throw new MojoExecutionException(format("%n%nFailed to create directory: %s", file));
        }
    }
}
