package io.github.fastsync.edt.upload;

import java.util.Locale;

/** Interprets the ibcmd config apply output without hiding the operator's decision. */
public final class WarningsParser
{
    private WarningsParser()
    {
    }

    /** True when the apply failure looks like a warning that can be confirmed with --force. */
    public static boolean requiresConfirmation(String output)
    {
        String lower = lower(output);
        return lower.contains("предупрежд") //$NON-NLS-1$
            || lower.contains("warning") //$NON-NLS-1$
            || lower.contains("требует подтверждения") //$NON-NLS-1$
            || lower.contains("requires confirmation") //$NON-NLS-1$
            || lower.contains("[y/n]") //$NON-NLS-1$
            || lower.contains("принять изменения") //$NON-NLS-1$
            || lower.contains("продолжить обновление"); //$NON-NLS-1$
    }

    /**
     * True when the apply is blocked by active sessions; the plugin must never terminate
     * sessions or pass --session-terminate=force.
     */
    public static boolean mentionsSessionTermination(String output)
    {
        String lower = lower(output);
        return (lower.contains("сеанс") || lower.contains("session")) //$NON-NLS-1$ //$NON-NLS-2$
            && (lower.contains("заверш") || lower.contains("terminat") || lower.contains("блокир") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                || lower.contains("lock")); //$NON-NLS-1$
    }

    private static String lower(String output)
    {
        return output == null ? "" : output.toLowerCase(Locale.ROOT); //$NON-NLS-1$
    }
}
