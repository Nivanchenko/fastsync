package io.github.fastsync.edt.upload;

import io.github.fastsync.edt.Messages;

/** Expected failure of the upload operation; carries a localized message key. */
public class UploadException extends Exception
{
    private static final long serialVersionUID = 1L;

    private final String messageKey;
    private final transient Object[] arguments;

    public UploadException(String messageKey, Object... arguments)
    {
        super(messageKey);
        this.messageKey = messageKey;
        this.arguments = arguments;
    }

    public UploadException(Throwable cause, String messageKey, Object... arguments)
    {
        super(messageKey, cause);
        this.messageKey = messageKey;
        this.arguments = arguments;
    }

    public String getMessageKey()
    {
        return messageKey;
    }

    @Override
    public String getLocalizedMessage()
    {
        return Messages.get(messageKey, arguments);
    }
}
