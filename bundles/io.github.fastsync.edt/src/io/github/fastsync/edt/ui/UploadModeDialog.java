package io.github.fastsync.edt.ui;

import java.util.Optional;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;

import io.github.fastsync.edt.Messages;
import io.github.fastsync.edt.upload.UploadMode;
import io.github.fastsync.edt.upload.UploadTarget;

/** Asks the user to choose the upload mode and shows the resolved target. */
public final class UploadModeDialog
{
    public Optional<UploadMode> open(Shell shell, UploadTarget target)
    {
        String message = Messages.get("dialog.mode.message", //$NON-NLS-1$
            target.project().getName(), target.infobasePath(), target.requiredVersion(), target.ibcmd());
        int choice = MessageDialog.open(MessageDialog.QUESTION, shell, Messages.get("dialog.title"), message, //$NON-NLS-1$
            0, Messages.get("dialog.mode.full"), Messages.get("dialog.mode.partial")); //$NON-NLS-1$ //$NON-NLS-2$
        if (choice == 0)
        {
            return Optional.of(UploadMode.FULL);
        }
        if (choice == 1)
        {
            return Optional.of(UploadMode.PARTIAL);
        }
        return Optional.empty();
    }
}
