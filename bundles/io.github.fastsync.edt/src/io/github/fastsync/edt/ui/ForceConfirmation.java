package io.github.fastsync.edt.ui;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;

import io.github.fastsync.edt.Messages;

/** Asks the user to confirm a retry of config apply with --force. */
public final class ForceConfirmation
{
    public boolean confirm(String output)
    {
        Display display = Display.getDefault();
        if (display == null)
        {
            return false;
        }
        boolean[] confirmed = new boolean[1];
        display.syncExec(() -> confirmed[0] = MessageDialog.openQuestion(display.getActiveShell(),
            Messages.get("dialog.force.title"), Messages.get("dialog.force.message", output))); //$NON-NLS-1$ //$NON-NLS-2$
        return confirmed[0];
    }
}
