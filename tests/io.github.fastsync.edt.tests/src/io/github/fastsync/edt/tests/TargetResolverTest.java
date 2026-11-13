package io.github.fastsync.edt.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com._1c.g5.v8.dt.core.platform.IDtProject;
import com._1c.g5.v8.dt.core.platform.IDtProjectManager;
import com._1c.g5.v8.dt.core.platform.IV8Project;
import com._1c.g5.v8.dt.core.platform.IV8ProjectManager;
import com._1c.g5.v8.dt.platform.services.core.infobases.IInfobaseAccessManager;
import com._1c.g5.v8.dt.platform.services.core.infobases.IInfobaseAccessSettings;
import com._1c.g5.v8.dt.platform.services.core.runtimes.IRuntimeInstallationManager;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.ComponentExecutorInfo;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.IDesignerSessionThickClientLauncher;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.ILaunchableRuntimeComponent;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.IRuntimeComponentManager;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.IRuntimeComponentTypes;
import com._1c.g5.v8.dt.platform.services.model.FileConnectionString;
import com._1c.g5.v8.dt.platform.services.model.InfobaseReference;
import com._1c.g5.v8.dt.platform.services.model.InfobaseType;
import com._1c.g5.v8.dt.platform.services.model.RuntimeInstallation;
import com._1c.g5.v8.dt.platform.version.Version;
import com.e1c.g5.dt.applications.IApplication;
import com.e1c.g5.dt.applications.IApplicationManager;
import com.e1c.g5.dt.applications.infobases.IInfobaseApplication;

import io.github.fastsync.edt.upload.TargetResolver;
import io.github.fastsync.edt.upload.UploadException;
import io.github.fastsync.edt.upload.UploadTarget;

public class TargetResolverTest
{
    private Path directory;
    private Path infobaseDirectory;
    private Path ibcmd;

    private IProject project;
    private IApplicationManager applicationManager;
    private IRuntimeInstallationManager installationManager;
    private IRuntimeComponentManager componentManager;
    private IV8ProjectManager projectManager;
    private IDtProjectManager dtProjectManager;
    private IInfobaseAccessManager accessManager;

    private IInfobaseApplication application;
    private InfobaseReference infobase;
    private FileConnectionString connectionString;
    private RuntimeInstallation installation;
    private IV8Project v8Project;

    @Before
    public void setUp() throws Exception
    {
        directory = Files.createTempDirectory("fastsync-resolver-");
        infobaseDirectory = Files.createDirectories(directory.resolve("ib"));
        Path installationDirectory = Files.createDirectories(directory.resolve("install"));
        Path thickClient = Files.writeString(installationDirectory.resolve("1cv8"), "");
        ibcmd = Files.writeString(installationDirectory.resolve(ibcmdName()), "");
        ibcmd.toFile().setExecutable(true);

        project = mock(IProject.class);
        when(project.getFullPath()).thenReturn(new org.eclipse.core.runtime.Path("/P"));

        applicationManager = mock(IApplicationManager.class);
        installationManager = mock(IRuntimeInstallationManager.class);
        componentManager = mock(IRuntimeComponentManager.class);
        projectManager = mock(IV8ProjectManager.class);
        dtProjectManager = mock(IDtProjectManager.class);
        accessManager = mock(IInfobaseAccessManager.class);

        application = mock(IInfobaseApplication.class);
        infobase = mock(InfobaseReference.class);
        connectionString = mock(FileConnectionString.class);
        installation = mock(RuntimeInstallation.class);
        ILaunchableRuntimeComponent component = mock(ILaunchableRuntimeComponent.class);
        IDesignerSessionThickClientLauncher launcher = mock(IDesignerSessionThickClientLauncher.class);
        v8Project = mock(IV8Project.class);

        when(applicationManager.getDefaultApplication(project)).thenReturn(Optional.of(application));
        when(application.getInfobase()).thenReturn(infobase);
        when(application.getRequiredVersion()).thenReturn(Optional.of("8.3.27.2342"));
        when(infobase.getInfobaseType()).thenReturn(InfobaseType.FILE);
        when(infobase.getConnectionString()).thenReturn(connectionString);
        when(connectionString.getFile()).thenReturn(infobaseDirectory.toString());

        when(installation.getVersionWithBuild()).thenReturn("8.3.27.2342");
        when(installation.getLocation()).thenReturn(installationDirectory.toUri());
        when(installationManager.getAllRegistered()).thenReturn(List.of(installation));

        when(component.getFile()).thenReturn(thickClient.toFile());
        when(componentManager.resolveExecutor(eq(ILaunchableRuntimeComponent.class),
            eq(IDesignerSessionThickClientLauncher.class), eq(installation), eq(IRuntimeComponentTypes.THICK_CLIENT)))
            .thenReturn(new ComponentExecutorInfo<>(installation, component, launcher));

        when(v8Project.getVersion()).thenReturn(new Version(8, 3, 27));
        when(projectManager.getProject(project)).thenReturn(v8Project);
        when(dtProjectManager.getDtProject(project)).thenReturn(mock(IDtProject.class));
        when(accessManager.resolveSettings(infobase)).thenReturn(IInfobaseAccessSettings.NOT_DEFINED);
    }

    @After
    public void tearDown() throws IOException
    {
        try (Stream<Path> stream = Files.walk(directory))
        {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    public void resolvesFileInfobaseAndIbcmd() throws Exception
    {
        UploadTarget target = resolver().resolve(project, new NullProgressMonitor());

        assertEquals(infobaseDirectory, target.infobasePath());
        assertEquals(ibcmd, target.ibcmd());
        assertEquals("8.3.27.2342", target.requiredVersion());
        assertNotNull(target.configurationVersion());
        assertNotNull(target.dtProject());
    }

    @Test
    public void failsWithoutDefaultApplication() throws Exception
    {
        when(applicationManager.getDefaultApplication(project)).thenReturn(Optional.empty());

        assertFailure("error.no.application");
    }

    @Test
    public void failsForNonInfobaseApplication() throws Exception
    {
        when(applicationManager.getDefaultApplication(project)).thenReturn(Optional.of(mock(IApplication.class)));

        assertFailure("error.not.infobase");
    }

    @Test
    public void failsForServerInfobase() throws Exception
    {
        when(infobase.getInfobaseType()).thenReturn(InfobaseType.SERVER);

        assertFailure("error.not.file.infobase");
    }

    @Test
    public void resolvesVersionFromInfobaseWhenApplicationVersionMissing() throws Exception
    {
        when(application.getRequiredVersion()).thenReturn(Optional.empty());
        when(infobase.getVersion()).thenReturn("8.3.27.2342");

        UploadTarget target = resolver().resolve(project, new NullProgressMonitor());

        assertEquals("8.3.27.2342", target.requiredVersion());
    }

    @Test
    public void failsWithoutProjectVersion() throws Exception
    {
        when(v8Project.getVersion()).thenReturn(null);

        assertFailure("error.no.version");
    }

    @Test
    public void failsWithoutMatchingInstallation() throws Exception
    {
        when(installationManager.getAllRegistered()).thenReturn(List.of());

        assertFailure("error.no.installation");
    }

    @Test
    public void failsWithoutIbcmd() throws Exception
    {
        Files.delete(ibcmd);

        assertFailure("error.ibcmd");
    }

    private void assertFailure(String expectedKey) throws Exception
    {
        try
        {
            resolver().resolve(project, new NullProgressMonitor());
            fail("Expected UploadException with key " + expectedKey);
        }
        catch (UploadException e)
        {
            assertEquals(expectedKey, e.getMessageKey());
        }
    }

    private TargetResolver resolver()
    {
        return new TargetResolver(applicationManager, installationManager, componentManager, projectManager,
            dtProjectManager, accessManager);
    }

    private static String ibcmdName()
    {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows")
            ? "ibcmd.exe" : "ibcmd";
    }
}
