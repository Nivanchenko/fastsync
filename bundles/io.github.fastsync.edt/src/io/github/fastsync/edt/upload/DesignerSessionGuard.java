package io.github.fastsync.edt.upload;

import java.nio.file.Path;

import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.ComponentExecutorInfo;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.IDesignerAgentConnectionListener;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.IDesignerSessionThickClientLauncher;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.ILaunchableRuntimeComponent;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.IRuntimeComponentManager;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.IRuntimeComponentTypes;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.RuntimeExecutionArguments;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.RuntimeExecutionException;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.RuntimeVersionRequiredException;
import com._1c.g5.v8.dt.platform.services.model.InfobaseReference;

/**
 * Closes the Designer Agent connection holding the file infobase lock and restores the
 * original connection state afterwards. The agent process itself is never terminated.
 */
public final class DesignerSessionGuard implements AutoCloseable
{
    private static final IDesignerAgentConnectionListener NOOP_LISTENER = new IDesignerAgentConnectionListener()
    {
        @Override
        public void closed()
        {
        }

        @Override
        public void reconnected(Path sessionDirectory)
        {
        }

        @Override
        public void subscribed(IDesignerAgentConnectionListener.Subscription subscription)
        {
        }
    };

    private final IDesignerSessionThickClientLauncher launcher;
    private final ILaunchableRuntimeComponent component;
    private final InfobaseReference infobase;
    private final RuntimeExecutionArguments arguments = new RuntimeExecutionArguments();
    private boolean wasConnected;
    private boolean closed;

    public DesignerSessionGuard(UploadTarget target) throws UploadException
    {
        IRuntimeComponentManager manager = Services.get(IRuntimeComponentManager.class);
        if (manager == null)
        {
            throw new UploadException("error.service", "IRuntimeComponentManager"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        ComponentExecutorInfo<ILaunchableRuntimeComponent, IDesignerSessionThickClientLauncher> info;
        try
        {
            info = manager.resolveExecutor(ILaunchableRuntimeComponent.class,
                IDesignerSessionThickClientLauncher.class, target.installation(),
                IRuntimeComponentTypes.THICK_CLIENT);
        }
        catch (RuntimeExecutionException e)
        {
            throw new UploadException(e, "error.session", e.getMessage()); //$NON-NLS-1$
        }
        this.launcher = info.getExecutor();
        this.component = target.thickClient();
        this.infobase = target.infobase();
    }

    /** Visible for tests: uses already resolved launcher/component/infobase. */
    public DesignerSessionGuard(IDesignerSessionThickClientLauncher launcher,
        ILaunchableRuntimeComponent component, InfobaseReference infobase)
    {
        this.launcher = launcher;
        this.component = component;
        this.infobase = infobase;
    }

    /** Closes the connection if it was open; aborts the operation when this is not safe. */
    public void closeSession() throws UploadException
    {
        try
        {
            wasConnected = launcher.getDesignerSessionDirectory(component, infobase, arguments) != null;
            if (wasConnected)
            {
                launcher.closeDesignerSession(component, infobase, arguments);
                closed = true;
            }
        }
        catch (RuntimeExecutionException | RuntimeVersionRequiredException e)
        {
            throw new UploadException(e, "error.session", e.getMessage()); //$NON-NLS-1$
        }
    }

    public boolean wasConnected()
    {
        return wasConnected;
    }

    /** Restores the connection exactly when this guard closed it. */
    public void restore() throws UploadException
    {
        if (!closed)
        {
            return;
        }
        try
        {
            launcher.openDesignerSession(component, infobase, NOOP_LISTENER, arguments);
        }
        catch (RuntimeExecutionException | RuntimeVersionRequiredException e)
        {
            throw new UploadException(e, "error.session.restore", e.getMessage()); //$NON-NLS-1$
        }
        finally
        {
            closed = false;
        }
    }

    @Override
    public void close()
    {
        try
        {
            restore();
        }
        catch (UploadException e)
        {
            // The caller reports the restore failure; a close() must not throw.
        }
    }
}
