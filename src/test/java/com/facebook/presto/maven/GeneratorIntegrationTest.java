package com.facebook.presto.maven;

import com.google.common.collect.ImmutableList;
import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readAllLines;
import static org.junit.Assert.assertEquals;

@RunWith(MavenJUnitTestRunner.class)
@MavenVersions("3.3.9")
@SuppressWarnings({"JUnitTestNG", "PublicField"})
public class GeneratorIntegrationTest
{
    private static final String PLUGIN_DESCRIPTOR = "META-INF/services/com.facebook.presto.spi.Plugin";
    private static final String COORDINATOR_PLUGIN_DESCRIPTOR = "META-INF/services/com.facebook.presto.spi.CoordinatorPlugin";
    private static final String ROUTER_PLUGIN_DESCRIPTOR = "META-INF/services/com.facebook.presto.spi.RouterPlugin";

    @Rule
    public final TestResources resources = new TestResources();

    public final MavenRuntime maven;

    public GeneratorIntegrationTest(MavenRuntimeBuilder mavenBuilder)
            throws Exception
    {
        this.maven = mavenBuilder.withCliOptions("-B", "-U").build();
    }

    @Test
    public void testBasic()
            throws Exception
    {
        File basedir = resources.getBasedir("basic");
        maven.forProject(basedir)
                .execute("package")
                .assertErrorFreeLog();

        File output = new File(basedir, "target/classes/" + PLUGIN_DESCRIPTOR);

        List<String> lines = readAllLines(output.toPath(), UTF_8);
        assertEquals(ImmutableList.of("its.BasicPlugin"), lines);
    }

    @Test
    public void testBasicCoordinatorPlugin()
            throws Exception
    {
        File basedir = resources.getBasedir("basic-coordinator-plugin");
        maven.forProject(basedir)
                .execute("package")
                .assertErrorFreeLog();

        File output = new File(basedir, "target/classes/" + COORDINATOR_PLUGIN_DESCRIPTOR);

        List<String> lines = readAllLines(output.toPath(), UTF_8);
        assertEquals(ImmutableList.of("its.BasicCoordinatorPlugin"), lines);
    }

    @Test
    public void testBasicRouterPlugin()
            throws Exception
    {
        File basedir = resources.getBasedir("basic-router-plugin");
        maven.forProject(basedir)
                .execute("package")
                .assertErrorFreeLog();

        File output = new File(basedir, "target/classes/" + ROUTER_PLUGIN_DESCRIPTOR);

        List<String> lines = readAllLines(output.toPath(), UTF_8);
        assertEquals(ImmutableList.of("its.BasicRouterPlugin"), lines);
    }

}
