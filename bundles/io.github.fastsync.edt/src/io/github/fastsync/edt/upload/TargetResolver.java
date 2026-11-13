package io.github.fastsync.edt.upload;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import com._1c.g5.v8.dt.core.platform.IDtProject;
import com._1c.g5.v8.dt.core.platform.IDtProjectManager;
import com._1c.g5.v8.dt.core.platform.IV8Project;
import com._1c.g5.v8.dt.core.platform.IV8ProjectManager;
import com._1c.g5.v8.dt.platform.services.core.infobases.IInfobaseAccessManager;
import com._1c.g5.v8.dt.platform.services.core.infobases.IInfobaseAccessSettings;
import com._1c.g5.v8.dt.platform.services.core.runtimes.IRuntimeInstallationManager;
import com._1c.g5.v8.dt.platform.services.core.runtimes.RuntimeInstallations;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.ComponentExecutorInfo;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.IDesignerSessionThickClientLauncher;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.ILaunchableRuntimeComponent;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.IRuntimeComponentManager;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.IRuntimeComponentTypes;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.RuntimeExecutionException;
import com._1c.g5.v8.dt.platform.services.model.FileConnectionString;
import com._1c.g5.v8.dt.platform.services.model.InfobaseReference;
import com._1c.g5.v8.dt.platform.services.model.InfobaseType;
import com._1c.g5.v8.dt.platform.services.model.RuntimeInstallation;
import com._1c.g5.wiring.ServiceAccess;
import com.e1c.g5.dt.applications.ApplicationException;
import com.e1c.g5.dt.applications.IApplication;
import com.e1c.g5.dt.applications.IApplicationManager;
import com.e1c.g5.dt.applications.infobases.IInfobaseApplication;

/** Resolves and validates the upload target from the project's default application. */
public final class TargetResolver
{
    private final IApplicationManager applicationManager;
    private final IRuntimeInstallationManager installationManager;
    private final IRuntimeComponentManager componentManager;
    private final IV8ProjectManager projectManager;
    private final IDtProjectManager dtProjectManager;
    private final IInfobaseAccessManager accessManager;

    public TargetResolver()
    {
        this(ServiceAccess.get(IApplicationManager.class), ServiceAccess.get(IRuntimeInstallationManager.class),
            ServiceAccess.get(IRuntimeComponentManager.class), ServiceAccess.get(IV8ProjectManager.class),
            ServiceAccess.get(IDtProjectManager.class), ServiceAccess.get(IInfobaseAccessManager.class));
    }

    public TargetResolver(IApplicationManager applicationManager,
        IRuntimeInstallationManager installationManager, IRuntimeComponentManager componentManager,
        IV8ProjectManager projectManager, IDtProjectManager dtProjectManager,
        IInfobaseAccessManager accessManager)
    {
        this.applicationManager = applicationManager;
        this.installationManager = installationManager;
        this.componentManager = componentManager;
        this.projectManager = projectManager;
        this.dtProjectManager = dtProjectManager;
        this.accessManager = accessManager;
    }

    public UploadTarget resolve(IProject project, IProgressMonitor monitor) throws UploadException
    {
        Objects.requireNonNull(project, "project"); //$NON-NLS-1$

        IApplication application;
        try
        {
            application = requireService(applicationManager, "IApplicationManager") //$NON-NLS-1$
                .getDefaultApplication(project)
                .orElseThrow(() -> new UploadException("error.no.application")); //$NON-NLS-1$
        }
        catch (ApplicationException e)
        {
            throw new UploadException(e, "error.no.application"); //$NON-NLS-1$
        }

        if (!(application instanceof IInfobaseApplication infobaseApplication))
        {
            throw new UploadException("error.not.infobase"); //$NON-NLS-1$
        }

        InfobaseReference infobase = infobaseApplication.getInfobase();
        if (infobase.getInfobaseType() != InfobaseType.FILE)
        {
            throw new UploadException("error.not.file.infobase"); //$NON-NLS-1$
        }
        if (!(infobase.getConnectionString() instanceof FileConnectionString fileConnectionString))
        {
            throw new UploadException("error.bad.connection"); //$NON-NLS-1$
        }

        Path infobasePath;
        try
        {
            infobasePath = Path.of(fileConnectionString.getFile()).toAbsolutePath().normalize();
        }
        catch (RuntimeException e)
        {
            throw new UploadException(e, "error.ib.path", fileConnectionString.getFile()); //$NON-NLS-1$
        }
        if (!Files.isDirectory(infobasePath))
        {
            throw new UploadException("error.ib.path", infobasePath); //$NON-NLS-1$
        }

        String requiredVersion = application.getRequiredVersion()
            .filter(version -> !version.isBlank())
            .orElseThrow(() -> new UploadException("error.no.version")); //$NON-NLS-1$

        List<RuntimeInstallation> matches = requireService(installationManager, "IRuntimeInstallationManager") //$NON-NLS-1$
            .getAllRegistered()
            .stream()
            .filter(RuntimeInstallations.filterByVersion(requiredVersion))
            .toList();
        if (matches.size() != 1)
        {
            throw new UploadException("error.no.installation", requiredVersion); //$NON-NLS-1$
        }
        RuntimeInstallation installation = matches.get(0);

        ComponentExecutorInfo<ILaunchableRuntimeComponent, IDesignerSessionThickClientLauncher> componentAndExecutor;
        try
        {
            componentAndExecutor = requireService(componentManager, "IRuntimeComponentManager") //$NON-NLS-1$
                .resolveExecutor(ILaunchableRuntimeComponent.class, IDesignerSessionThickClientLauncher.class,
                    installation, IRuntimeComponentTypes.THICK_CLIENT);
        }
        catch (RuntimeExecutionException e)
        {
            throw new UploadException(e, "error.no.installation", requiredVersion); //$NON-NLS-1$
        }
        ILaunchableRuntimeComponent thickClient = componentAndExecutor.getComponent();
        Path ibcmd = resolveIbcmd(installation, thickClient);
        if (!Files.isExecutable(ibcmd))
        {
            throw new UploadException("error.ibcmd", ibcmd); //$NON-NLS-1$
        }

        IV8Project v8Project = requireService(projectManager, "IV8ProjectManager").getProject(project); //$NON-NLS-1$
        if (v8Project == null || v8Project.getVersion() == null)
        {
            throw new UploadException("error.no.version"); //$NON-NLS-1$
        }
        IDtProject dtProject = requireService(dtProjectManager, "IDtProjectManager").getDtProject(project); //$NON-NLS-1$

        IInfobaseAccessSettings accessSettings = IInfobaseAccessSettings.NOT_DEFINED;
        if (accessManager != null)
        {
            try
            {
                IInfobaseAccessSettings resolved = accessManager.resolveSettings(infobase);
                if (resolved != null)
                {
                    accessSettings = resolved;
                }
            }
            catch (CoreException e)
            {
                // The file infobase normally needs no credentials; keep the default settings.
            }
        }

        return new UploadTarget(project, dtProject, application, infobase, infobasePath, requiredVersion,
            installation, thickClient, ibcmd, v8Project.getVersion(), accessSettings);
    }

    private Path resolveIbcmd(RuntimeInstallation installation, ILaunchableRuntimeComponent thickClient)
    {
        String name = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            ? "ibcmd.exe" : "ibcmd"; //$NON-NLS-1$ //$NON-NLS-2$
        List<Path> candidates = new ArrayList<>();
        File executable = thickClient == null ? null : thickClient.getFile();
        if (executable != null && executable.toPath().toAbsolutePath().getParent() != null)
        {
            candidates.add(executable.toPath().toAbsolutePath().getParent().resolve(name));
        }
        try
        {
            if (installation.getLocation() != null)
            {
                Path root = Path.of(installation.getLocation());
                candidates.add(root.resolve(name));
                candidates.add(root.resolve("bin").resolve(name)); //$NON-NLS-1$
            }
        }
        catch (RuntimeException e)
        {
            // Ignore a malformed installation location; the component path is the primary source.
        }
        for (Path candidate : candidates)
        {
            if (Files.isExecutable(candidate))
            {
                return candidate;
            }
        }
        return candidates.isEmpty() ? Path.of(name) : candidates.get(0);
    }

    private <T> T requireService(T service, String name) throws UploadException
    {
        if (service == null)
        {
            throw new UploadException("error.service", name); //$NON-NLS-1$
        }
        return service;
    }
}
