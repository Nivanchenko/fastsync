package io.github.fastsync.edt.upload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

import com._1c.g5.v8.dt.core.platform.IExtensionProject;
import com._1c.g5.v8.dt.core.platform.IV8ProjectManager;
import com._1c.g5.v8.dt.platform.services.core.infobases.IInfobasePreferencesManager;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.IInfobaseSynchronizationManager;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.v2.IInfobaseSynchronizationStateManager;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.IInfobaseUpdateCallback;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.InfobaseConflictResolutionResult;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.InfobaseConflictResolution;
import com._1c.g5.v8.dt.platform.services.model.InfobaseReference;

import io.github.fastsync.edt.Activator;
import io.github.fastsync.edt.Messages;
import io.github.fastsync.edt.ui.ForceConfirmation;

/** Runs one upload operation: save, convert, import with ibcmd, apply and sync EDT state. */
public final class UploadOperation
{
    private static final Set<String> ACTIVE = ConcurrentHashMap.newKeySet();
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"); //$NON-NLS-1$

    /**
     * Extensions are loaded by the standard EDT mechanism, which should apply their changes.
     * A proxy keeps the internal {@code IDbStructureChange} parameter out of our compile classpath.
     */
    private static final IInfobaseUpdateCallback CONFIRM_EXTENSION_CHANGES = confirmExtensionChanges();

    private static IInfobaseUpdateCallback confirmExtensionChanges()
    {
        return (IInfobaseUpdateCallback) Proxy.newProxyInstance(UploadOperation.class.getClassLoader(),
            new Class<?>[] { IInfobaseUpdateCallback.class }, (proxy, method, args) ->
            {
                if ("onConfirm".equals(method.getName())) //$NON-NLS-1$
                {
                    return Boolean.TRUE;
                }
                if ("resolveInfobaseChanges".equals(method.getName())) //$NON-NLS-1$
                {
                    return new InfobaseConflictResolution(InfobaseConflictResolutionResult.IGNORED);
                }
                return null;
            });
    }

    private final IProject project;
    private final UploadTarget target;
    private final UploadMode requestedMode;
    private final Predicate<String> forceConfirmation;
    private final UploadReport report = new UploadReport();

    public UploadOperation(IProject project, UploadTarget target, UploadMode requestedMode)
    {
        this(project, target, requestedMode, new ForceConfirmation()::confirm);
    }

    public UploadOperation(IProject project, UploadTarget target, UploadMode requestedMode,
        Predicate<String> forceConfirmation)
    {
        this.project = project;
        this.target = target;
        this.requestedMode = requestedMode;
        this.forceConfirmation = forceConfirmation;
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
        DesignerSessionGuard designerSession = null;
        try
        {
            operationDirectory = Files.createTempDirectory("fastsync-upload-"); //$NON-NLS-1$
            IbcmdCommandBuilder builder = new IbcmdCommandBuilder(target, operationDirectory);
            builder.prepareDirectories();
            IbcmdRunner runner = new IbcmdRunner();
            SourceExporter exporter = new SourceExporter();

            report.setStage(UploadStage.SAVE);
            checkCancelled(monitor);
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
                    // shortcut: EDT's timestamp-based dirty check may see changes the flow cannot
                    // collect; prefer a safe full reload over reporting "nothing to upload".
                    if (!isProjectDirty())
                    {
                        String diagnostics = Messages.get("log.diagnostic", project.getName(), //$NON-NLS-1$
                            target.infobasePath(), equalityState(), hasSynchronizationInfo(), isProjectDirty());
                        Activator.log(IStatus.WARNING, diagnostics, null);
                        listener.outputLine(diagnostics);
                        report.setStage(UploadStage.DONE);
                        report.setSummary(Messages.get("report.nothing"), true); //$NON-NLS-1$
                        return report;
                    }
                    listener.outputLine(Messages.get("report.dirtyFallback")); //$NON-NLS-1$
                    full = true;
                }
                else
                {
                    full = changes.requiresFullReload();
                    if (full)
                    {
                        listener.outputLine(Messages.get("report.fallback")); //$NON-NLS-1$
                    }
                }
            }
            report.setActualMode(full ? UploadMode.FULL : UploadMode.PARTIAL);
            if (full)
            {
                // The standard EDT full load does the same before importing.
                session.setFullReloadRequired();
            }

            long configStart = System.currentTimeMillis();
            report.setStage(UploadStage.EXPORT);
            checkCancelled(monitor);
            Path xmlDirectory = operationDirectory.resolve("xml"); //$NON-NLS-1$
            SourceExporter.ExportResult export = full
                ? exporter.exportFull(project, target, xmlDirectory, monitor)
                : exporter.exportPartial(project, target, changes, xmlDirectory, monitor);

            report.setStage(UploadStage.SESSION);
            checkCancelled(monitor);
            designerSession = new DesignerSessionGuard(target);
            designerSession.closeSession();

            report.setStage(UploadStage.IMPORT);
            checkCancelled(monitor);
            IbcmdRunner.Result importResult = runCommand(runner,
                full ? builder.importFull(export.directory())
                    : builder.importPartial(export.directory(), export.files()),
                monitor, listener);
            if (importResult.exitCode() != 0)
            {
                throw new UploadException("error.import", importResult.exitCode(), tail(importResult.output())); //$NON-NLS-1$
            }

            report.setStage(UploadStage.APPLY);
            checkCancelled(monitor);
            applyConfiguration(runner, builder, monitor, listener);

            report.setStage(UploadStage.SYNC_STATE);
            checkCancelled(monitor);
            Path dumpDirectory = operationDirectory.resolve("dump"); //$NON-NLS-1$
            Files.createDirectories(dumpDirectory);
            String generationId = generationId(runner, builder, monitor, listener);
            session.setActualGenerationId(generationId);
            runCommand(runner, builder.exportInfo(dumpDirectory), monitor, listener);
            session.loadActualConfigDumpInfo(dumpDirectory);
            session.finish();

            List<String> timeline = new ArrayList<>();
            timeline.add(timelineLine(configStart, System.currentTimeMillis(),
                full ? Messages.get("report.config.full") : Messages.get("report.config.partial"), true)); //$NON-NLS-1$ //$NON-NLS-2$
            boolean extensionsOk = loadExtensions(monitor, listener, timeline);

            report.setStage(UploadStage.DONE);
            report.setTimeline(String.join(System.lineSeparator(), timeline));
            report.setSummary(extensionsOk
                ? Messages.get("report.success", target.infobasePath()) //$NON-NLS-1$
                : Messages.get("report.extensionsFailed"), extensionsOk); //$NON-NLS-1$
        }
        catch (UploadCancelledException e)
        {
            report.setSummary(Messages.get("report.cancelled", ""), false); //$NON-NLS-1$ //$NON-NLS-2$
        }
        catch (UploadException e)
        {
            Activator.log(IStatus.ERROR, e.getLocalizedMessage(), e);
            report.setSummary(e.getLocalizedMessage(), false);
        }
        catch (IOException e)
        {
            Activator.log(IStatus.ERROR, e.getMessage(), e);
            report.setSummary(Messages.get("error.export", e.getMessage()), false); //$NON-NLS-1$
        }
        finally
        {
            if (designerSession != null)
            {
                try
                {
                    designerSession.restore();
                }
                catch (UploadException e)
                {
                    report.appendSummary(e.getLocalizedMessage());
                }
            }
            if (session != null)
            {
                session.close();
            }
            deleteTree(operationDirectory);
            ACTIVE.remove(key);
        }
        return report;
    }

    private void applyConfiguration(IbcmdRunner runner, IbcmdCommandBuilder builder, IProgressMonitor monitor,
        IbcmdRunner.Listener listener) throws UploadException
    {
        if (!asksForConfirmation())
        {
            // The infobase preference explicitly disables confirmation: no doomed first attempt.
            listener.outputLine(Messages.get("console.autoForce")); //$NON-NLS-1$
            IbcmdRunner.Result forced = runCommand(runner, builder.apply(true), monitor, listener);
            if (forced.exitCode() != 0)
            {
                if (WarningsParser.mentionsSessionTermination(forced.output()))
                {
                    throw new UploadException("error.sessionsBlocked"); //$NON-NLS-1$
                }
                throw new UploadException("error.apply", forced.exitCode(), tail(forced.output())); //$NON-NLS-1$
            }
            return;
        }

        IbcmdRunner.Result result = runCommand(runner, builder.apply(false), monitor, listener);
        if (result.exitCode() == 0)
        {
            return;
        }
        String output = result.output();
        if (WarningsParser.mentionsSessionTermination(output))
        {
            throw new UploadException("error.sessionsBlocked"); //$NON-NLS-1$
        }
        // Code 102 is returned when the apply needs an explicit confirmation as well.
        if (!WarningsParser.requiresConfirmation(output) && result.exitCode() != 102)
        {
            throw new UploadException("error.apply", result.exitCode(), tail(output)); //$NON-NLS-1$
        }
        if (!forceConfirmation.test(output))
        {
            throw new UploadException("report.importedNotApplied"); //$NON-NLS-1$
        }
        listener.outputLine(Messages.get("console.confirmed")); //$NON-NLS-1$
        IbcmdInvocation forcedApply = builder.apply(true);
        IbcmdRunner.Result forced = runCommand(runner, forcedApply, monitor, listener, "y\n"); //$NON-NLS-1$
        if (forced.exitCode() != 0)
        {
            if (WarningsParser.mentionsSessionTermination(forced.output()))
            {
                throw new UploadException("error.sessionsBlocked"); //$NON-NLS-1$
            }
            throw new UploadException("error.apply", forced.exitCode(), tail(forced.output())); //$NON-NLS-1$
        }
    }

    private IbcmdRunner.Result runCommand(IbcmdRunner runner, IbcmdInvocation invocation, IProgressMonitor monitor,
        IbcmdRunner.Listener listener) throws UploadException
    {
        return runCommand(runner, invocation, monitor, listener, null);
    }

    private IbcmdRunner.Result runCommand(IbcmdRunner runner, IbcmdInvocation invocation, IProgressMonitor monitor,
        IbcmdRunner.Listener listener, String stdin) throws UploadException
    {
        listener.outputLine(Messages.get("console.command", invocation.display())); //$NON-NLS-1$
        IbcmdRunner.Result result = runner.run(invocation, monitor, listener, stdin);
        listener.outputLine(Messages.get("console.exit", result.exitCode())); //$NON-NLS-1$
        report.command(invocation.display(), result.exitCode());
        if (result.cancelled())
        {
            throw new UploadCancelledException();
        }
        return result;
    }

    /** Loads every extension of the project into the infobase with the standard EDT mechanism. */
    private boolean loadExtensions(IProgressMonitor monitor, IbcmdRunner.Listener listener, List<String> timeline)
        throws UploadCancelledException
    {
        IV8ProjectManager projects = Services.get(IV8ProjectManager.class);
        IInfobaseSynchronizationManager synchronization = Services.get(IInfobaseSynchronizationManager.class);
        if (projects == null || synchronization == null)
        {
            return true;
        }
        List<IExtensionProject> extensions = projects.getProjects(IExtensionProject.class).stream()
            .filter(extension -> project.equals(extension.getParentProject()))
            .sorted(Comparator.comparing(this::extensionName))
            .toList();
        boolean ok = true;
        for (IExtensionProject extension : extensions)
        {
            checkCancelled(monitor);
            String name = extensionName(extension);
            long start = System.currentTimeMillis();
            boolean extensionOk;
            try
            {
                IStatus status = synchronization.updateInfobase(extension.getProject(), target.infobase(),
                    CONFIRM_EXTENSION_CHANGES, true, monitor);
                extensionOk = status == null || status.isOK();
                if (!extensionOk)
                {
                    listener.outputLine(Messages.get("console.extensionFailed", name, //$NON-NLS-1$
                        status.getMessage() == null ? "" : status.getMessage())); //$NON-NLS-1$ //$NON-NLS-2$
                }
            }
            catch (RuntimeException e)
            {
                extensionOk = false;
                listener.outputLine(Messages.get("console.extensionFailed", name, //$NON-NLS-1$
                    e.getMessage() == null ? "" : e.getMessage())); //$NON-NLS-1$ //$NON-NLS-2$
            }
            timeline.add(timelineLine(start, System.currentTimeMillis(),
                Messages.get("report.extension", name), extensionOk)); //$NON-NLS-1$
            ok &= extensionOk;
        }
        return ok;
    }

    private String extensionName(IExtensionProject extension)
    {
        String name = extension.getConfiguration() == null ? null : extension.getConfiguration().getName();
        return name == null || name.isBlank() ? extension.getProject().getName() : name;
    }

    private static String timelineLine(long start, long end, String action, boolean ok)
    {
        String timestamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(end), ZoneId.systemDefault())
            .format(TIMESTAMP);
        return Messages.get("report.timeline.line", timestamp, action, //$NON-NLS-1$
            Messages.get(ok ? "report.status.ok" : "report.status.error"), duration(end - start)); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static String duration(long millis)
    {
        long seconds = Math.max(0, millis / 1000);
        long minutes = seconds / 60;
        long rest = seconds % 60;
        return minutes > 0
            ? Messages.get("duration.minutes", minutes) + " " + Messages.get("duration.seconds", rest) //$NON-NLS-1$ //$NON-NLS-2$
            : Messages.get("duration.seconds", rest); //$NON-NLS-1$
    }

    private String generationId(IbcmdRunner runner, IbcmdCommandBuilder builder, IProgressMonitor monitor,
        IbcmdRunner.Listener listener) throws UploadException
    {
        listener.outputLine(Messages.get("console.command", builder.generationId().display())); //$NON-NLS-1$
        IbcmdRunner.Result result = runner.run(builder.generationId(), monitor, listener);
        listener.outputLine(Messages.get("console.exit", result.exitCode())); //$NON-NLS-1$
        report.command(builder.generationId().display(), result.exitCode());
        if (result.cancelled())
        {
            throw new UploadCancelledException();
        }
        if (result.exitCode() != 0)
        {
            throw new UploadException("error.sync", tail(result.output())); //$NON-NLS-1$
        }
        String[] lines = result.output().strip().split("\\R"); //$NON-NLS-1$
        for (int i = lines.length - 1; i >= 0; i--)
        {
            String line = lines[i].strip();
            if (!line.isEmpty())
            {
                return line;
            }
        }
        throw new UploadException("error.sync", "empty generation id"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static String tail(String output)
    {
        if (output == null)
        {
            return ""; //$NON-NLS-1$
        }
        String trimmed = output.strip();
        int max = 1200;
        return trimmed.length() <= max ? trimmed : trimmed.substring(trimmed.length() - max);
    }

    private boolean asksForConfirmation()
    {
        IInfobasePreferencesManager preferences = Services.get(IInfobasePreferencesManager.class);
        if (preferences == null)
        {
            return true;
        }
        try
        {
            return preferences.getPromptConfirmationOnRestructure(target.infobase().getUuid());
        }
        catch (RuntimeException e)
        {
            return true;
        }
    }

    private void checkCancelled(IProgressMonitor monitor) throws UploadCancelledException
    {
        if (monitor.isCanceled())
        {
            throw new UploadCancelledException();
        }
    }

    private String equalityState()
    {
        IInfobaseSynchronizationManager manager = Services.get(IInfobaseSynchronizationManager.class);
        try
        {
            return manager == null ? "n/a" : String.valueOf(manager.getEqualityState(project, target.infobase())); //$NON-NLS-1$
        }
        catch (RuntimeException e)
        {
            return "n/a"; //$NON-NLS-1$
        }
    }

    private boolean hasSynchronizationInfo()
    {
        IInfobaseSynchronizationStateManager manager = Services.get(IInfobaseSynchronizationStateManager.class);
        try
        {
            return manager != null && manager.hasSynchronizationInfo(project, target.infobase());
        }
        catch (RuntimeException e)
        {
            return false;
        }
    }

    @SuppressWarnings("removal")
    private boolean isProjectDirty()
    {
        IInfobaseSynchronizationStateManager manager = Services.get(IInfobaseSynchronizationStateManager.class);
        try
        {
            return manager != null && manager.isProjectDirty(target.dtProject(), target.infobase());
        }
        catch (RuntimeException e)
        {
            return false;
        }
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
        checkCancelled(monitor);
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
