package io.github.fastsync.edt.upload;

/** Marker for a user-requested cancellation of the upload operation. */
public final class UploadCancelledException extends UploadException
{
    private static final long serialVersionUID = 1L;

    public UploadCancelledException()
    {
        super("report.cancelled", ""); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
