package io.github.fastsync.edt;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/** Access to localized messages. */
public final class Messages
{
    private static final String BUNDLE_NAME = "io.github.fastsync.edt.messages"; //$NON-NLS-1$

    private Messages()
    {
    }

    public static String get(String key, Object... arguments)
    {
        String pattern;
        try
        {
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME, Locale.getDefault(),
                Messages.class.getClassLoader());
            pattern = bundle.getString(key);
        }
        catch (MissingResourceException e)
        {
            return key;
        }
        return arguments.length == 0 ? pattern : MessageFormat.format(pattern, arguments);
    }
}
