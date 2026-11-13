package io.github.fastsync.edt.ui;

import java.util.Optional;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import io.github.fastsync.edt.Messages;
import io.github.fastsync.edt.upload.TargetResolver;
import io.github.fastsync.edt.upload.UploadException;
import io.github.fastsync.edt.upload.UploadMode;
import io.github.fastsync.edt.upload.UploadOperation;
import io.github.fastsync.edt.upload.UploadTarget;

/** Command handler for the explicit "upload sources via ibcmd" action. */
public final class UploadSourcesHandler extends AbstractHandler
{
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException
    {
        Shell shell = HandlerUtil.getActiveShell(event);
        IStructuredSelection selection = HandlerUtil.getCurrentStructuredSelection(event);
        Object first = selection == null ? null : selection.getFirstElement();
        if (!(first instanceof IProject project))
        {
            MessageDialog.openError(shell, Messages.get("dialog.title"), Messages.get("error.project")); //$NON-NLS-1$ //$NON-NLS-2$
            return null;
        }
        if (UploadOperation.isBusy(project))
        {
            MessageDialog.openInformation(shell, Messages.get("dialog.title"), //$NON-NLS-1$
                Messages.get("error.busy", project.getName())); //$NON-NLS-1$
            return null;
        }

        UploadTarget target;
        try
        {
            target = new TargetResolver().resolve(project, new NullProgressMonitor());
        }
        catch (UploadException e)
        {
            MessageDialog.openError(shell, Messages.get("dialog.title"), e.getLocalizedMessage()); //$NON-NLS-1$
            return null;
        }

        Optional<UploadMode> mode = new UploadModeDialog().open(shell, target);
        if (mode.isEmpty())
        {
            return null;
        }

        UploadJob job = new UploadJob(new UploadOperation(project, target, mode.get()), new ConsoleOutput());
        job.schedule();
        return null;
    }
}
