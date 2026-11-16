package io.github.fastsync.edt.upload;

import java.util.ArrayList;
import java.util.List;

import io.github.fastsync.edt.Messages;

/** Accumulates the user-visible report of one upload operation. */
public final class UploadReport
{
    private final List<String> commands = new ArrayList<>();
    private final List<Integer> exitCodes = new ArrayList<>();
    private UploadStage stage = UploadStage.PRECHECK;
    private UploadMode actualMode;
    private boolean success;
    private String summary = ""; //$NON-NLS-1$
    private String timeline = ""; //$NON-NLS-1$

    public UploadStage getStage()
    {
        return stage;
    }

    public void setStage(UploadStage stage)
    {
        this.stage = stage;
    }

    public UploadMode getActualMode()
    {
        return actualMode;
    }

    public void setActualMode(UploadMode actualMode)
    {
        this.actualMode = actualMode;
    }

    public void command(String displayCommand, int exitCode)
    {
        commands.add(displayCommand);
        exitCodes.add(exitCode);
    }

    public void setSummary(String summary, boolean success)
    {
        this.summary = summary;
        this.success = success;
    }

    public void appendSummary(String text)
    {
        this.summary = summary.isEmpty() ? text : summary + System.lineSeparator() + text;
        this.success = false;
    }

    public boolean isSuccess()
    {
        return success;
    }

    /** One line per performed action: timestamp, action, status, duration. */
    public void setTimeline(String timeline)
    {
        this.timeline = timeline;
    }

    public String getSummary()
    {
        return summary;
    }

    public String format()
    {
        if (!timeline.isEmpty())
        {
            return timeline;
        }
        StringBuilder builder = new StringBuilder(summary);
        if (actualMode != null)
        {
            builder.append(System.lineSeparator()).append(Messages.get("report.mode", actualMode.getLabel())); //$NON-NLS-1$
        }
        for (int i = 0; i < commands.size(); i++)
        {
            builder.append(System.lineSeparator()).append(Messages.get("report.command", commands.get(i), //$NON-NLS-1$
                exitCodes.get(i)));
        }
        return builder.toString();
    }
}
