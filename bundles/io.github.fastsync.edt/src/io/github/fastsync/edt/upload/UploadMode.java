package io.github.fastsync.edt.upload;

import io.github.fastsync.edt.Messages;

/** Requested upload mode. */
public enum UploadMode
{
    FULL("mode.full"), //$NON-NLS-1$
    PARTIAL("mode.partial"); //$NON-NLS-1$

    private final String messageKey;

    UploadMode(String messageKey)
    {
        this.messageKey = messageKey;
    }

    public String getLabel()
    {
        return Messages.get(messageKey);
    }
}
