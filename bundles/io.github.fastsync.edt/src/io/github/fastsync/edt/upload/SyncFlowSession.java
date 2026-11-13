package io.github.fastsync.edt.upload;

import java.nio.file.Path;
import java.util.List;

import org.apache.commons.lang3.tuple.Triple;
import org.eclipse.core.resources.IProject;
import org.eclipse.emf.ecore.EObject;

import com._1c.g5.v8.dt.core.operations.ProjectPipelineJob;
import com._1c.g5.v8.dt.core.platform.IDtProject;
import com._1c.g5.v8.dt.core.platform.IDtProjectManager;
import com._1c.g5.v8.dt.core.platform.IWorkspaceOrchestrator;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.v2.IInfobaseSynchronizationStateManager;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.v2.IUpdateInfobaseFlow;
import com._1c.g5.wiring.ServiceAccess;

/**
 * Wraps the public v2 synchronization flow used to collect unsynchronized changes and to
 * publish the actual infobase state back to EDT. Internal EDT packages are not referenced.
 */
public final class SyncFlowSession implements AutoCloseable
{
    private static final String SERVICE_NAME = "IInfobaseSynchronizationStateManager"; //$NON-NLS-1$

    private final IWorkspaceOrchestrator orchestrator;
    private final IDtProject dtProject;
    private final IUpdateInfobaseFlow flow;
    private boolean finished;

    private SyncFlowSession(IWorkspaceOrchestrator orchestrator, IDtProject dtProject, IUpdateInfobaseFlow flow)
    {
        this.orchestrator = orchestrator;
        this.dtProject = dtProject;
        this.flow = flow;
    }

    public static SyncFlowSession start(UploadTarget target) throws UploadException
    {
        IInfobaseSynchronizationStateManager manager = ServiceAccess.get(IInfobaseSynchronizationStateManager.class);
        if (manager == null)
        {
            throw new UploadException("error.service", SERVICE_NAME); //$NON-NLS-1$
        }
        if (target.dtProject() == null)
        {
            throw new UploadException("error.service", "IDtProject"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        try
        {
            IUpdateInfobaseFlow flow = manager.startUpdateInfobaseFlow(target.dtProject(), target.infobase());
            flow.start();
            return new SyncFlowSession(ServiceAccess.get(IWorkspaceOrchestrator.class), target.dtProject(), flow);
        }
        catch (RuntimeException e)
        {
            throw new UploadException(e, "error.sync"); //$NON-NLS-1$
        }
    }

    public ChangeSet collectChanges() throws UploadException
    {
        Object operation = null;
        if (orchestrator != null)
        {
            operation = orchestrator.beginBackgroundOperation("FastSync", List.of(dtProject), ProjectPipelineJob.IMPORT); //$NON-NLS-1$
        }
        try
        {
            Triple<java.util.Set<EObject>, java.util.Set<EObject>, java.util.Set<String>> changes =
                flow.collectEdtObjectChanges();
            return new ChangeSet(changes.getLeft(), changes.getMiddle(), changes.getRight());
        }
        catch (RuntimeException e)
        {
            throw new UploadException(e, "error.sync"); //$NON-NLS-1$
        }
        finally
        {
            if (orchestrator != null && operation != null)
            {
                orchestrator.endOperation(operation);
            }
        }
    }

    public void loadActualConfigDumpInfo(Path configDumpInfo) throws UploadException
    {
        try
        {
            flow.loadActualConfigDumpInfo(configDumpInfo);
        }
        catch (RuntimeException e)
        {
            throw new UploadException(e, "error.sync"); //$NON-NLS-1$
        }
    }

    public void finish() throws UploadException
    {
        try
        {
            flow.finish();
            finished = true;
        }
        catch (RuntimeException e)
        {
            throw new UploadException(e, "error.sync"); //$NON-NLS-1$
        }
    }

    public void cancel()
    {
        if (finished)
        {
            return;
        }
        try
        {
            flow.cancel();
        }
        catch (RuntimeException e)
        {
            // Cancellation is best effort; the original failure is reported by the caller.
        }
        finally
        {
            finished = true;
        }
    }

    @Override
    public void close()
    {
        cancel();
    }
}
