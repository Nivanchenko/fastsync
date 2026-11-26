package io.github.fastsync.edt.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import org.junit.Test;

import io.github.fastsync.edt.Messages;

/**
 * Tycho/p2 and OSGi read localized .properties as ISO-8859-1, so raw UTF-8 in them turns into
 * mojibake in the update site. The resources must therefore decode correctly through
 * Properties.load(InputStream) and contain no leftover mojibake characters.
 */
public class LocalizationTest
{
    private static final String MESSAGES_RU = "/io/github/fastsync/edt/messages_ru.properties";
    private static final String PLUGIN_RU = "/plugin_ru.properties";

    @Test
    public void russianStringsSurviveIso88591Reading() throws IOException
    {
        Properties messages = load(MESSAGES_RU);
        assertEquals("Полная загрузка", messages.getProperty("mode.full"));
        assertEquals("Импорт конфигурации", messages.getProperty("stage.import"));

        Properties plugin = load(PLUGIN_RU);
        assertEquals("FastSync для 1C:EDT", plugin.getProperty("pluginName"));
        assertEquals("Загрузить исходники в ИБ через ibcmd", plugin.getProperty("upload.command.name"));
    }

    @Test
    public void russianResourcesContainNoMojibake() throws IOException
    {
        assertNoMojibake(MESSAGES_RU);
        assertNoMojibake(PLUGIN_RU);
    }

    private static Properties load(String resource) throws IOException
    {
        try (InputStream stream = Messages.class.getResourceAsStream(resource))
        {
            assertNotNull("missing resource " + resource, stream);
            Properties properties = new Properties();
            properties.load(stream);
            return properties;
        }
    }

    /**
     * Mojibake signature: UTF-8 Cyrillic bytes read as ISO-8859-1 always start with U+00D0 or
     * U+00D1, and U+FFFD marks a byte undefined in a single-byte encoding. Other Latin-1
     * characters (e.g. escaped «» or a no-break space) are legitimate text, not mojibake.
     */
    private static void assertNoMojibake(String resource) throws IOException
    {
        for (Map.Entry<Object, Object> entry : load(resource).entrySet())
        {
            String value = (String) entry.getValue();
            for (int i = 0; i < value.length(); i++)
            {
                char c = value.charAt(i);
                if (c == '\u00d0' || c == '\u00d1' || c == '\ufffd')
                {
                    fail(resource + " " + entry.getKey() + " contains mojibake character U+"
                        + String.format("%04X", (int) c) + ": " + value);
                }
            }
        }
    }
}
