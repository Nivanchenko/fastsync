package io.github.fastsync.edt.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;

import org.junit.Test;

import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.IDesignerSessionThickClientLauncher;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.ILaunchableRuntimeComponent;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.RuntimeExecutionException;
import com._1c.g5.v8.dt.platform.services.model.InfobaseReference;

import io.github.fastsync.edt.upload.DesignerSessionGuard;
import io.github.fastsync.edt.upload.UploadException;

public class DesignerSessionGuardTest
{
    private final IDesignerSessionThickClientLauncher launcher =
        mock(IDesignerSessionThickClientLauncher.class);
    private final ILaunchableRuntimeComponent component = mock(ILaunchableRuntimeComponent.class);
    private final InfobaseReference infobase = mock(InfobaseReference.class);

    @Test
    public void closesAndRestoresAnOpenSession() throws Exception
    {
        when(launcher.getDesignerSessionDirectory(eq(component), eq(infobase), any()))
            .thenReturn(Path.of("/tmp/designer-session"));

        DesignerSessionGuard guard = new DesignerSessionGuard(launcher, component, infobase);
        guard.closeSession();
        guard.restore();

        verify(launcher).closeDesignerSession(eq(component), eq(infobase), any());
        verify(launcher).openDesignerSession(eq(component), eq(infobase), any(), any());
    }

    @Test
    public void doesNotTouchAClosedSession() throws Exception
    {
        when(launcher.getDesignerSessionDirectory(eq(component), eq(infobase), any())).thenReturn(null);

        DesignerSessionGuard guard = new DesignerSessionGuard(launcher, component, infobase);
        guard.closeSession();
        guard.restore();

        verify(launcher, never()).closeDesignerSession(any(), any(), any());
        verify(launcher, never()).openDesignerSession(any(), any(), any(), any());
    }

    @Test
    public void failsBeforeImportWhenSessionCannotBeClosed() throws Exception
    {
        when(launcher.getDesignerSessionDirectory(eq(component), eq(infobase), any()))
            .thenThrow(new RuntimeExecutionException("boom"));

        try
        {
            new DesignerSessionGuard(launcher, component, infobase).closeSession();
            fail("Expected UploadException");
        }
        catch (UploadException e)
        {
            assertEquals("error.session", e.getMessageKey());
        }
        verify(launcher, never()).closeDesignerSession(any(), any(), any());
    }

    @Test
    public void reportsRestoreFailure()
    {
        try
        {
            when(launcher.getDesignerSessionDirectory(eq(component), eq(infobase), any()))
                .thenReturn(Path.of("/tmp/designer-session"));
            when(launcher.openDesignerSession(any(), any(), any(), any()))
                .thenThrow(new RuntimeExecutionException("boom"));
            DesignerSessionGuard guard = new DesignerSessionGuard(launcher, component, infobase);
            guard.closeSession();
            guard.restore();
            fail("Expected UploadException");
        }
        catch (UploadException e)
        {
            assertEquals("error.session.restore", e.getMessageKey());
        }
        catch (Exception e)
        {
            fail("Unexpected exception: " + e);
        }
    }
}
