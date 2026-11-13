package io.github.fastsync.edt.upload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

import io.github.fastsync.edt.Messages;

/** Runs one upload operation: save, convert, import with ibcmd, apply and sync EDT state. */
public final class UploadOperation
{
    private static final Set<String> ACTIVE = ConcurrentHashMap.newKeySet();

    private final IProject project;
    private final UploadTarget target;
    private final UploadMode requestedMode;
    private final UploadReport report = new UploadReport();

    public UploadOperation(IProject project, UploadTarget target, UploadMode requestedMode)
    {
        this.project = project;
        this.target = target;
        this.requestedMode = requestedMode;
    }

    public IProject getProject()
    {
        return project;
    }

    public UploadReport getReport()
    {
        return report;
    }

    public static boolean isBusy(IProject project)
    {
        return ACTIVE.contains(key(project));
    }

    public UploadReport run(IProgressMonitor monitor, IbcmdRunner.Listener listener)
    {
        String key = key(project);
        if (!ACTIVE.add(key))
        {
            report.setSummary(Messages.get("error.busy", project.getName()), false); //$NON-NLS-1$
            return report;
        }
        Path operationDirectory = null;
        SyncFlowSession session = null;
        try
        {
            operationDirectory = Files.createTempDirectory("fastsync-upload-"); //$NON-NLS-1$
            IbcmdCommandBuilder builder = new IbcmdCommandBuilder(target, operationDirectory);
            builder.prepareDirectories();
            IbcmdRunner runner = new IbcmdRunner();
            SourceExporter exporter = new SourceExporter();

            report.setStage(UploadStage.SAVE);
            saveProject(monitor);

            report.setStage(UploadStage.CHANGES);
            session = SyncFlowSession.start(target);

            boolean full = requestedMode == UploadMode.FULL;
            ChangeSet changes = null;
            if (!full)
            {
                changes = session.collectChanges();
                if (changes.isEmpty())
                {
                    report.setStage(UploadStage.DONE);
                    report.setSummary(Messages.get("report.nothing"), true); //$NON-NLS-1$
                    return report;
                }
                full = changes.requiresFullReload();
                if (full)
                {
                    listener.outputLine(Messages.get("report.fallback")); //$NON-NLS-1$
                }
            }
            report.setActualMode(full ? UploadMode.FULL : UploadMode.PARTIAL);

            report.setStage(UploadStage.EXPORT);
            Path xmlDirectory = operationDirectory.resolve("xml"); //$NON-NLS-1$
            SourceExporter.ExportResult export = full
                ? exporter.exportFull(project, target, xmlDirectory, monitor)
                : exporter.exportPartial(project, target, changes, xmlDirectory, monitor);

            report.setStage(UploadStage.IMPORT);
            IbcmdRunner.Result importResult = runCommand(runner,
                full ? builder.importFull(export.directory())
                    : builder.importPartial(export.directory(), export.files()),
                monitor, listener);
            if (importResult.exitCode() != 0)
            {
                throw new UploadException("error.import", importResult.exitCode()); //$NON-NLS-1$
            }

            report.setStage(UploadStage.APPLY);
            IbcmdRunner.Result applyResult = runCommand(runner, builder.apply(false), monitor, listener);
            if (applyResult.exitCode() != 0)
            {
                throw new UploadException("error.apply", applyResult.exitCode()); //$NON-NLS-1$
            }

            report.setStage(UploadStage.SYNC_STATE);
            Path dumpDirectory = operationDirectory.resolve("dump"); //$NON-NLS-1$
            Files.createDirectories(dumpDirectory);
            runCommand(runner, builder.exportInfo(dumpDirectory), monitor, listener);
            session.loadActualConfigDumpInfo(dumpDirectory.resolve("ConfigDumpInfo.xml")); //$NON-NLS-1$
            session.finish();

            report.setStage(UploadStage.DONE);
            report.setSummary(Messages.get("report.success", target.infobasePath()), true); //$NON-NLS-1$
        }
        catch (UploadCancelledException e)
        {
            report.setSummary(Messages.get("report.cancelled", ""), false); //$NON-NLS-1$ //$NON-NLS-2$
        }
        catch (UploadException e)
        {
            report.setSummary(e.getLocalizedMessage(), false);
        }
        catch (IOException e)
        {
            report.setSummary(Messages.get("error.export", e.getMessage()), false); //$NON-NLS-1$
        }
        finally
        {
            if (session != null)
            {
                session.close();
            }
            deleteTree(operationDirectory);
            ACTIVE.remove(key);
        }
        return report;
    }

    private IbcmdRunner.Result runCommand(IbcmdRunner runner, IbcmdInvocation invocation, IProgressMonitor monitor,
        IbcmdRunner.Listener listener) throws UploadException
    {
        listener.outputLine(Messages.get("console.command", invocation.display())); //$NON-NLS-1$
        IbcmdRunner.Result result = runner.run(invocation, monitor, listener);
        listener.outputLine(Messages.get("console.exit", result.exitCode())); //$NON-NLS-1$
        report.command(invocation.display(), result.exitCode());
        if (result.cancelled())
        {
            throw new UploadCancelledException();
        }
        return result;
    }

    private void saveProject(IProgressMonitor monitor) throws UploadException
    {
        Display display = Display.getDefault();
        if (display == null)
        {
            return;
        }
        Boolean[] saved = new Boolean[1];
        display.syncExec(() -> saved[0] = PlatformUI.getWorkbench().saveAllEditors(true));
        if (Boolean.FALSE.equals(saved[0]))
        {
            throw new UploadException("error.save"); //$NON-NLS-1$
        }
        if (monitor.isCanceled())
        {
            throw new UploadCancelledException();
        }
    }

    private static void deleteTree(Path directory)
    {
        if (directory == null || !Files.exists(directory))
        {
            return;
        }
        try (Stream<Path> stream = Files.walk(directory))
        {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try
                {
                    Files.deleteIfExists(path);
                }
                catch (IOException e)
                {
                    // Temporary files are best effort; the operation result is already known.
                }
            });
        }
        catch (IOException e)
        {
            // Ignore cleanup failures, they must not hide the operation result.
        }
    }

    private static String key(IProject project)
    {
        return project.getFullPath().toString();
    }
}
