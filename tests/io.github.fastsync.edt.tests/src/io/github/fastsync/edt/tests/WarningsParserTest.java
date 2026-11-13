package io.github.fastsync.edt.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.github.fastsync.edt.upload.WarningsParser;

public class WarningsParserTest
{
    @Test
    public void recognizesWarningsRequiringConfirmation()
    {
        assertTrue(WarningsParser.requiresConfirmation("Обнаружены предупреждения"));
        assertTrue(WarningsParser.requiresConfirmation("WARNING: structure changes"));
        assertFalse(WarningsParser.requiresConfirmation("Произошла ошибка"));
    }

    @Test
    public void recognizesSessionBlockers()
    {
        assertTrue(WarningsParser.mentionsSessionTermination("Не удалось заблокировать базу: активны сеансы"));
        assertTrue(WarningsParser.mentionsSessionTermination("Session lock failed: terminate sessions"));
        assertFalse(WarningsParser.mentionsSessionTermination("Все хорошо"));
    }
}
