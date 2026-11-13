package io.github.fastsync.edt.upload;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.IProgressMonitor;

/** Runs one ibcmd process, streams its output and supports cancellation. */
public final class IbcmdRunner
{
    /** Receives unmasked output lines for the console. */
    public interface Listener
    {
        void outputLine(String line);
    }

    public record Result(int exitCode, String output, boolean cancelled)
    {
    }

    public Result run(IbcmdInvocation invocation, IProgressMonitor monitor, Listener listener) throws UploadException
    {
        return run(invocation, monitor, listener, null);
    }

    /** Runs the command, optionally answering an interactive prompt after user confirmation. */
    public Result run(IbcmdInvocation invocation, IProgressMonitor monitor, Listener listener, String stdin)
        throws UploadException
    {
        ProcessBuilder builder = new ProcessBuilder(invocation.arguments());
        builder.redirectErrorStream(true);
        Process process;
        try
        {
            process = builder.start();
        }
        catch (IOException e)
        {
            throw new UploadException(e, "error.ibcmd.start", e.getMessage()); //$NON-NLS-1$
        }

        if (stdin != null)
        {
            try (java.io.OutputStream input = process.getOutputStream())
            {
                input.write(stdin.getBytes(StandardCharsets.UTF_8));
                input.flush();
            }
            catch (IOException e)
            {
                throw new UploadException(e, "error.ibcmd.start", e.getMessage()); //$NON-NLS-1$
            }
        }

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (monitor.isCanceled())
                {
                    terminate(process);
                    return new Result(-1, output.toString(), true);
                }
                output.append(line).append('\n');
                if (listener != null)
                {
                    listener.outputLine(mask(line, invocation));
                }
            }
            int exitCode = process.waitFor();
            return new Result(exitCode, output.toString(), monitor.isCanceled());
        }
        catch (IOException e)
        {
            terminate(process);
            throw new UploadException(e, "error.ibcmd.start", e.getMessage()); //$NON-NLS-1$
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            terminate(process);
            throw new UploadCancelledException();
        }
    }

    private static String mask(String line, IbcmdInvocation invocation)
    {
        String masked = line;
        List<String> arguments = invocation.arguments();
        for (int index : invocation.secretIndexes())
        {
            String argument = arguments.get(index);
            int separator = argument.indexOf('=');
            if (separator >= 0)
            {
                String secret = argument.substring(separator + 1);
                if (!secret.isEmpty())
                {
                    masked = masked.replace(secret, "***");
                }
            }
        }
        return masked;
    }

    public static void terminate(Process process)
    {
        process.destroy();
        try
        {
            if (!process.waitFor(5, TimeUnit.SECONDS))
            {
                process.destroyForcibly();
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
