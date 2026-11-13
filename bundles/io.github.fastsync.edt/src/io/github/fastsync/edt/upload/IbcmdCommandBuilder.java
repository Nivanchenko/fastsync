package io.github.fastsync.edt.upload;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com._1c.g5.v8.dt.platform.services.core.infobases.IInfobaseAccessSettings;

/** Builds {@code ibcmd} invocations for the target infobase (see contracts/ibcmd-contract.md). */
public final class IbcmdCommandBuilder
{
    private final UploadTarget target;
    private final Path dataDirectory;
    private final Path tempDirectory;

    public IbcmdCommandBuilder(UploadTarget target, Path operationDirectory)
    {
        this.target = target;
        this.dataDirectory = operationDirectory.resolve("server-data"); //$NON-NLS-1$
        this.tempDirectory = operationDirectory.resolve("server-temp"); //$NON-NLS-1$
    }

    public IbcmdInvocation importFull(Path xmlDirectory)
    {
        List<String> arguments = baseArguments();
        arguments.add("config"); //$NON-NLS-1$
        arguments.add("import"); //$NON-NLS-1$
        arguments.add(xmlDirectory.toString());
        return invocation(arguments);
    }

    public IbcmdInvocation importPartial(Path xmlDirectory, List<Path> files) throws UploadException
    {
        if (files.isEmpty())
        {
            throw new UploadException("report.nothing"); //$NON-NLS-1$
        }
        List<String> arguments = baseArguments();
        arguments.add("config"); //$NON-NLS-1$
        arguments.add("import"); //$NON-NLS-1$
        arguments.add("files"); //$NON-NLS-1$
        arguments.add("--partial"); //$NON-NLS-1$
        arguments.add("--base-dir=" + xmlDirectory); //$NON-NLS-1$
        for (Path file : files)
        {
            arguments.add(xmlDirectory.relativize(file).toString());
        }
        return invocation(arguments);
    }

    public IbcmdInvocation apply(boolean force)
    {
        List<String> arguments = baseArguments();
        arguments.add("config"); //$NON-NLS-1$
        arguments.add("apply"); //$NON-NLS-1$
        if (force)
        {
            arguments.add("--force"); //$NON-NLS-1$
        }
        return invocation(arguments);
    }

    public IbcmdInvocation exportInfo(Path outputDirectory)
    {
        List<String> arguments = baseArguments();
        arguments.add("config"); //$NON-NLS-1$
        arguments.add("export"); //$NON-NLS-1$
        arguments.add("info"); //$NON-NLS-1$
        arguments.add("--out=" + outputDirectory); //$NON-NLS-1$
        return invocation(arguments);
    }

    /** Creates the private server directories; never uses the shared standalone-server data. */
    public void prepareDirectories() throws UploadException
    {
        try
        {
            Files.createDirectories(dataDirectory);
            Files.createDirectories(tempDirectory);
        }
        catch (Exception e)
        {
            throw new UploadException(e, "error.ibcmd.start", dataDirectory.getParent()); //$NON-NLS-1$
        }
    }

    private List<String> baseArguments()
    {
        List<String> arguments = new ArrayList<>();
        arguments.add(target.ibcmd().toString());
        arguments.add("infobase"); //$NON-NLS-1$
        arguments.add("--data=" + dataDirectory); //$NON-NLS-1$
        arguments.add("--temp=" + tempDirectory); //$NON-NLS-1$
        arguments.add("--db-path=" + target.infobasePath()); //$NON-NLS-1$
        IInfobaseAccessSettings access = target.accessSettings();
        if (access != null)
        {
            String user = access.userName();
            if (user != null && !user.isBlank())
            {
                arguments.add("--user=" + user); //$NON-NLS-1$
            }
            String password = access.password();
            if (password != null && !password.isEmpty())
            {
                arguments.add("--password=" + password); //$NON-NLS-1$
            }
        }
        return arguments;
    }

    private IbcmdInvocation invocation(List<String> arguments)
    {
        for (int i = 0; i < arguments.size(); i++)
        {
            if (arguments.get(i).startsWith("--password=")) //$NON-NLS-1$
            {
                return new IbcmdInvocation(arguments, java.util.Set.of(i));
            }
        }
        return new IbcmdInvocation(arguments, java.util.Set.of());
    }
}
