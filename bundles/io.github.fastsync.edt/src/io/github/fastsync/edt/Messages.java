package io.github.fastsync.edt;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.eclipse.core.runtime.Platform;

/** Access to localized messages. */
public final class Messages
{
    private static final String BUNDLE_NAME = "io.github.fastsync.edt.messages"; //$NON-NLS-1$

    private Messages()
    {
    }

    public static String get(String key, Object... arguments)
    {
        return get(uiLocale(), key, arguments);
    }

    /** Uses the Eclipse UI language, so the plugin follows the EDT locale, not the OS locale. */
    public static String get(Locale locale, String key, Object... arguments)
    {
        String pattern;
        try
        {
            // No default-locale fallback: otherwise an English request would pick messages_ru
            // when the JVM default locale is Russian.
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale,
                Messages.class.getClassLoader(),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
            pattern = bundle.getString(key);
        }
        catch (MissingResourceException e)
        {
            return key;
        }
        return arguments.length == 0 ? pattern : MessageFormat.format(pattern, arguments);
    }

    private static Locale uiLocale()
    {
        try
        {
            String nl = Platform.getNL();
            if (nl != null && !nl.isBlank())
            {
                return Locale.forLanguageTag(nl.replace('_', '-'));
            }
        }
        catch (RuntimeException e)
        {
            // Outside an Eclipse runtime (e.g. unit tests): fall back to the JVM locale.
        }
        return Locale.getDefault();
    }
}
