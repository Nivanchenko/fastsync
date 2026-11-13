package io.github.fastsync.edt.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.github.fastsync.edt.upload.UploadMode;
import io.github.fastsync.edt.upload.UploadReport;

public class UploadReportTest
{
    @Test
    public void formatsSummaryModeAndCommands()
    {
        UploadReport report = new UploadReport();
        report.setActualMode(UploadMode.FULL);
        report.setSummary("Uploaded", true);
        report.command("ibcmd config import /tmp/xml", 0);
        report.command("ibcmd config apply", 0);

        String text = report.format();

        assertTrue(report.isSuccess());
        assertTrue(text.contains("Uploaded"));
        assertTrue(text.contains("ibcmd config import /tmp/xml"));
        assertTrue(text.contains("ibcmd config apply"));
    }

    @Test
    public void appendSummaryKeepsBothMessagesAndMarksFailure()
    {
        UploadReport report = new UploadReport();
        report.setSummary("primary", false);
        report.appendSummary("restore failed");

        assertEquals("primary" + System.lineSeparator() + "restore failed", report.getSummary());
        assertFalse(report.isSuccess());
    }
}
