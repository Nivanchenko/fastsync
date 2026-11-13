package io.github.fastsync.edt.upload;

import io.github.fastsync.edt.Messages;

/** Stages of the upload operation, used for progress and reports. */
public enum UploadStage
{
    PRECHECK("stage.precheck"), //$NON-NLS-1$
    SAVE("stage.save"), //$NON-NLS-1$
    CHANGES("stage.changes"), //$NON-NLS-1$
    EXPORT("stage.export"), //$NON-NLS-1$
    SESSION("stage.session"), //$NON-NLS-1$
    IMPORT("stage.import"), //$NON-NLS-1$
    APPLY("stage.apply"), //$NON-NLS-1$
    SYNC_STATE("stage.sync"), //$NON-NLS-1$
    DONE("stage.done"); //$NON-NLS-1$

    private final String messageKey;

    UploadStage(String messageKey)
    {
        this.messageKey = messageKey;
    }

    public String getLabel()
    {
        return Messages.get(messageKey);
    }
}
