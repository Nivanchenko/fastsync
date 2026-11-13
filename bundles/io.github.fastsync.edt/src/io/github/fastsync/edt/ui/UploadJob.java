package io.github.fastsync.edt.ui;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;

import io.github.fastsync.edt.Activator;
import io.github.fastsync.edt.Messages;
import io.github.fastsync.edt.upload.UploadOperation;
import io.github.fastsync.edt.upload.UploadReport;

/** Background job that runs the upload and reports the result on the UI thread. */
public final class UploadJob extends Job
{
    private final UploadOperation operation;
    private final ConsoleOutput console;

    public UploadJob(UploadOperation operation, ConsoleOutput console)
    {
        super(Messages.get("job.name", operation.getProject().getName())); //$NON-NLS-1$
        this.operation = operation;
        this.console = console;
        setUser(true);
    }

    @Override
    protected IStatus run(IProgressMonitor monitor)
    {
        console.activate();
        UploadReport report;
        try
        {
            report = operation.run(monitor, console::writeLine);
        }
        finally
        {
            console.close();
        }
        String text = report.format();
        Display display = Display.getDefault();
        if (display != null)
        {
            display.asyncExec(() -> showResult(report, text));
        }
        return report.isSuccess() ? Status.OK_STATUS
            : new Status(IStatus.ERROR, Activator.PLUGIN_ID, text);
    }

    private void showResult(UploadReport report, String text)
    {
        if (report.isSuccess())
        {
            MessageDialog.openInformation(Display.getDefault().getActiveShell(), Messages.get("dialog.title"), //$NON-NLS-1$
                text);
        }
        else
        {
            MessageDialog.openError(Display.getDefault().getActiveShell(), Messages.get("dialog.title"), text); //$NON-NLS-1$
        }
    }
}
