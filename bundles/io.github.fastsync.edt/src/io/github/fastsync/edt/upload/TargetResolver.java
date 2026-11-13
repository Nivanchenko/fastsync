package io.github.fastsync.edt.upload;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import com._1c.g5.v8.dt.core.platform.IDtProject;
import com._1c.g5.v8.dt.core.platform.IDtProjectManager;
import com._1c.g5.v8.dt.core.platform.IV8Project;
import com._1c.g5.v8.dt.core.platform.IV8ProjectManager;
import com._1c.g5.v8.dt.platform.services.core.infobases.IInfobaseAccessManager;
import com._1c.g5.v8.dt.platform.services.core.infobases.InfobaseAccessType;
import com._1c.g5.v8.dt.platform.services.core.infobases.IInfobaseAccessSettings;
import com._1c.g5.v8.dt.platform.services.core.runtimes.IRuntimeInstallationManager;
import com._1c.g5.v8.dt.platform.services.core.runtimes.RuntimeInstallations;
import com._1c.g5.v8.dt.platform.services.core.runtimes.environments.IResolvableRuntimeInstallation;
import com._1c.g5.v8.dt.platform.services.core.runtimes.environments.IResolvableRuntimeInstallationManager;
import com._1c.g5.v8.dt.platform.services.core.runtimes.environments.MatchingRuntimeNotFound;
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
import com.e1c.g5.dt.applications.ApplicationException;
import com.e1c.g5.dt.applications.IApplication;
import com.e1c.g5.dt.applications.IApplicationManager;
import com.e1c.g5.dt.applications.infobases.IInfobaseApplication;

import io.github.fastsync.edt.Activator;

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
        this(null, null, null, null, null, null);
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
            application = service(applicationManager, IApplicationManager.class, "IApplicationManager") //$NON-NLS-1$
                .getDefaultApplication(project)
                .orElseThrow(() -> new UploadException("error.no.application")); //$NON-NLS-1$
        }
        catch (ApplicationException e)
        {
            throw new UploadException(e, "error.no.application"); //$NON-NLS-1$
        }
        return resolve(project, application, monitor);
    }

    /** Resolves the target for the explicitly selected application. */
    public UploadTarget resolve(IProject project, IApplication application, IProgressMonitor monitor)
        throws UploadException
    {
        Objects.requireNonNull(project, "project"); //$NON-NLS-1$
        Objects.requireNonNull(application, "application"); //$NON-NLS-1$

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

        IV8Project v8Project = service(projectManager, IV8ProjectManager.class, "IV8ProjectManager") //$NON-NLS-1$
            .getProject(project);
        if (v8Project == null || v8Project.getVersion() == null)
        {
            throw new UploadException("error.no.version"); //$NON-NLS-1$
        }

        List<RuntimeInstallation> installations = installationManager == null
            ? enterpriseInstallations() : installationManager.getAllRegistered();
        if (installations == null)
        {
            throw new UploadException("error.service", "IRuntimeInstallationManager"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        // Let EDT pick the installation it uses for this project/infobase when possible.
        RuntimeInstallation installation = selectedInstallation(project, infobase, installations);
        String requiredVersion;
        if (installation != null)
        {
            requiredVersion = installation.getVersionWithBuild();
        }
        else
        {
            // The application version is optional; fall back to the infobase/project version.
            List<String> candidateVersions = new ArrayList<>();
            application.getRequiredVersion().filter(version -> !version.isBlank()).ifPresent(candidateVersions::add);
            addVersion(candidateVersions, infobase.getVersion());
            addVersion(candidateVersions, infobase.getDefaultVersion());
            addVersion(candidateVersions, v8Project.getVersion().toString());

            List<RuntimeInstallation> matches = List.of();
            requiredVersion = candidateVersions.isEmpty() ? "" : candidateVersions.get(0); //$NON-NLS-1$
            for (String candidate : candidateVersions)
            {
                try
                {
                    matches = installations.stream().filter(RuntimeInstallations.filterByVersion(candidate))
                        .toList();
                }
                catch (RuntimeException e)
                {
                    matches = List.of();
                }
                if (!matches.isEmpty())
                {
                    // Several patch builds of the same line may be installed; use the newest one.
                    installation = matches.stream().max(RuntimeInstallations.orderByVersion())
                        .orElse(matches.get(0));
                    requiredVersion = installation.getVersionWithBuild();
                    break;
                }
            }
            if (installation == null)
            {
                throw new UploadException("error.no.installation", //$NON-NLS-1$
                    String.join(", ", candidateVersions)); //$NON-NLS-1$
            }
        }

        ComponentExecutorInfo<ILaunchableRuntimeComponent, IDesignerSessionThickClientLauncher> componentAndExecutor;
        try
        {
            componentAndExecutor = service(componentManager, IRuntimeComponentManager.class, "IRuntimeComponentManager") //$NON-NLS-1$
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

        IDtProject dtProject = service(dtProjectManager, IDtProjectManager.class, "IDtProjectManager") //$NON-NLS-1$
            .getDtProject(project);

        IInfobaseAccessManager access = accessManager != null ? accessManager : Services.get(IInfobaseAccessManager.class);
        IInfobaseAccessSettings accessSettings = IInfobaseAccessSettings.NOT_DEFINED;
        if (access != null)
        {
            try
            {
                IInfobaseAccessSettings resolved = access.resolveSettings(infobase);
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

    /** There may be several installation managers (enterprise/mobile); pick the enterprise one. */
    private List<RuntimeInstallation> enterpriseInstallations()
    {
        Activator activator = Activator.getDefault();
        BundleContext context = activator == null ? null : activator.getBundle().getBundleContext();
        if (context == null)
        {
            return null;
        }
        try
        {
            Collection<ServiceReference<IRuntimeInstallationManager>> references =
                context.getServiceReferences(IRuntimeInstallationManager.class, null);
            List<RuntimeInstallation> installations = new ArrayList<>();
            for (ServiceReference<IRuntimeInstallationManager> reference : references)
            {
                IRuntimeInstallationManager manager = context.getService(reference);
                try
                {
                    if (manager != null
                        && RuntimeInstallations.ENTERPRISE_PLATFORM.equals(manager.getRuntimeTypeId()))
                    {
                        installations.addAll(manager.getAllRegistered());
                    }
                }
                finally
                {
                    context.ungetService(reference);
                }
            }
            return installations;
        }
        catch (InvalidSyntaxException e)
        {
            return null;
        }
    }

    private RuntimeInstallation selectedInstallation(IProject project, InfobaseReference infobase,
        List<RuntimeInstallation> installations)
    {
        IResolvableRuntimeInstallationManager manager =
            Services.get(IResolvableRuntimeInstallationManager.class);
        if (manager == null)
        {
            return null;
        }
        try
        {
            IResolvableRuntimeInstallation resolvable = manager.resolveByProjectAndInfobase(
                RuntimeInstallations.ENTERPRISE_PLATFORM, project, infobase, InfobaseAccessType.UPDATE);
            RuntimeInstallation resolved = resolvable.resolve(List.<String>of(), null);
            if (resolved != null && installations.stream()
                .anyMatch(candidate -> RuntimeInstallations.equals(candidate, resolved)))
            {
                return resolved;
            }
        }
        catch (RuntimeException e)
        {
            // Fall back to the version-based lookup below.
        }
        return null;
    }

    private static void addVersion(List<String> versions, String version)
    {
        if (version != null && !version.isBlank() && !versions.contains(version))
        {
            versions.add(version);
        }
    }

    private <T> T service(T injected, Class<T> type, String name) throws UploadException
    {
        T service = injected != null ? injected : Services.get(type);
        if (service == null)
        {
            throw new UploadException("error.service", name); //$NON-NLS-1$
        }
        return service;
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
}
