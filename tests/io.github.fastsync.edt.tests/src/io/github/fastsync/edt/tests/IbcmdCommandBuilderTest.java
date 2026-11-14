package io.github.fastsync.edt.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.junit.Test;

import com._1c.g5.v8.dt.core.platform.IDtProject;
import com._1c.g5.v8.dt.platform.services.core.infobases.IInfobaseAccessSettings;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.ILaunchableRuntimeComponent;
import com._1c.g5.v8.dt.platform.services.model.InfobaseReference;
import com._1c.g5.v8.dt.platform.version.Version;
import com.e1c.g5.dt.applications.IApplication;

import io.github.fastsync.edt.upload.IbcmdCommandBuilder;
import io.github.fastsync.edt.upload.IbcmdInvocation;
import io.github.fastsync.edt.upload.UploadTarget;

public class IbcmdCommandBuilderTest
{
    private static final Path IBCMD = Path.of("/opt/1cv8/8.3.27.2342/ibcmd");
    private static final Path INFOBASE = Path.of("/data/infobase");
    private static final Path OPERATION = Path.of("/tmp/fastsync-operation");

    @Test
    public void fullImportUsesPrivateDataDirectories()
    {
        IbcmdInvocation invocation = builder(IInfobaseAccessSettings.NOT_DEFINED).importFull(Path.of("/tmp/xml"));

        List<String> arguments = invocation.arguments();
        assertEquals(IBCMD.toString(), arguments.get(0));
        assertEquals("infobase", arguments.get(1));
        assertTrue(arguments.contains("--data=" + OPERATION.resolve("server-data")));
        assertTrue(arguments.contains("--temp=" + OPERATION.resolve("server-temp")));
        assertTrue(arguments.contains("--db-path=" + INFOBASE));
        assertEquals(List.of("config", "import", "/tmp/xml"), arguments.subList(arguments.size() - 3, arguments.size()));
    }

    @Test
    public void partialImportUsesRelativeFileList() throws Exception
    {
        Path xml = Path.of("/tmp/xml");
        IbcmdInvocation invocation = builder(IInfobaseAccessSettings.NOT_DEFINED)
            .importPartial(xml, List.of(xml.resolve("Documents/Приказ.xml")));

        assertTrue(invocation.arguments().contains("--partial"));
        assertTrue(invocation.arguments().contains("--base-dir=" + xml));
        assertEquals("Documents/Приказ.xml", invocation.arguments().get(invocation.arguments().size() - 1));
    }

    @Test
    public void applyAddsForceOnlyWhenAsked()
    {
        IbcmdCommandBuilder builder = builder(IInfobaseAccessSettings.NOT_DEFINED);

        assertFalse(builder.apply(false).arguments().contains("--force"));
        assertTrue(builder.apply(true).arguments().contains("--force"));
        assertFalse(builder.apply(true).arguments().contains("--session-terminate=force"));
    }

    @Test
    public void passwordIsMaskedInDisplayedCommand()
    {
        IInfobaseAccessSettings access = mock(IInfobaseAccessSettings.class);
        when(access.userName()).thenReturn("admin");
        when(access.password()).thenReturn("secret");

        IbcmdInvocation invocation = builder(access).apply(false);

        assertTrue(invocation.arguments().contains("--password=secret"));
        // Credentials are config-subcommand options and must follow "config", not "infobase".
        assertTrue(invocation.arguments().indexOf("config") < invocation.arguments().indexOf("--user=admin"));
        assertTrue(invocation.display().contains("--password=***"));
        assertFalse(invocation.display().contains("secret"));
    }

    private IbcmdCommandBuilder builder(IInfobaseAccessSettings access)
    {
        IApplication application = mock(IApplication.class);
        InfobaseReference infobase = mock(InfobaseReference.class);
        UploadTarget target = new UploadTarget(mock(IProject.class), mock(IDtProject.class), application, infobase,
            INFOBASE, "8.3.27.2342", null, mock(ILaunchableRuntimeComponent.class), IBCMD, new Version(8, 3, 27),
            access);
        return new IbcmdCommandBuilder(target, OPERATION);
    }
}
