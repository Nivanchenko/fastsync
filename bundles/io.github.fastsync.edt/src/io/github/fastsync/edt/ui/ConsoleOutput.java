package io.github.fastsync.edt.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.IOConsoleOutputStream;
import org.eclipse.ui.console.MessageConsole;

import io.github.fastsync.edt.Activator;
import io.github.fastsync.edt.Messages;

/** Dedicated "FastSync ibcmd" console that shows commands and their output. */
public final class ConsoleOutput implements AutoCloseable
{
    private final MessageConsole console;
    private final IOConsoleOutputStream stream;

    public ConsoleOutput()
    {
        console = newConsole();
        IConsoleManager manager = ConsolePlugin.getDefault().getConsoleManager();
        manager.addConsoles(new IConsole[] { console });
        stream = console.newOutputStream();
    }

    static MessageConsole newConsole()
    {
        // Explicit encoding: we write UTF-8 bytes, while Eclipse would default to the workbench
        // encoding (windows-1251 on Russian Windows) and show mojibake.
        return new MessageConsole(Messages.get("console.name"), StandardCharsets.UTF_8.name(), null, true); //$NON-NLS-1$
    }

    public void activate()
    {
        Display display = Display.getDefault();
        if (display != null)
        {
            display.asyncExec(() -> ConsolePlugin.getDefault().getConsoleManager().showConsoleView(console));
        }
    }

    public synchronized void writeLine(String text)
    {
        try
        {
            stream.write((text + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            stream.flush();
        }
        catch (IOException e)
        {
            Activator.getDefault().getLog().error("Cannot write to FastSync console", e); //$NON-NLS-1$
        }
    }

    @Override
    public void close()
    {
        try
        {
            stream.close();
        }
        catch (IOException e)
        {
            Activator.getDefault().getLog().error("Cannot close FastSync console stream", e); //$NON-NLS-1$
        }
    }
}
