package io.github.fastsync.edt.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.Locale;

import org.junit.Test;

import io.github.fastsync.edt.Messages;

public class MessagesTest
{
    @Test
    public void russianLocaleUsesRussianStrings()
    {
        assertEquals("Полная загрузка", Messages.get(Locale.forLanguageTag("ru"), "mode.full"));
    }

    @Test
    public void otherLocalesFallBackToEnglish()
    {
        String english = Messages.get(Locale.ENGLISH, "mode.full");
        assertEquals("Full upload", english);
        assertNotEquals(Messages.get(Locale.forLanguageTag("ru"), "mode.full"), english);
    }
}
