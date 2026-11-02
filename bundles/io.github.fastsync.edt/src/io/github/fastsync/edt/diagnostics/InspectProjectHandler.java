package io.github.fastsync.edt.diagnostics;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import com._1c.g5.v8.dt.platform.services.core.runtimes.IRuntimeInstallationManager;
import com._1c.g5.v8.dt.platform.services.core.runtimes.RuntimeInstallations;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.ComponentExecutorInfo;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.IDesignerSessionThickClientLauncher;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.ILaunchableRuntimeComponent;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.IRuntimeComponentManager;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.IRuntimeComponentTypes;
import com._1c.g5.v8.dt.platform.services.model.FileConnectionString;
import com._1c.g5.v8.dt.platform.services.model.InfobaseReference;
import com._1c.g5.v8.dt.platform.services.model.RuntimeInstallation;
import com._1c.g5.wiring.ServiceAccess;
import com.e1c.g5.dt.applications.IApplication;
import com.e1c.g5.dt.applications.IApplicationManager;
import com.e1c.g5.dt.applications.infobases.IInfobaseApplication;

import io.github.fastsync.edt.Activator;

public class InspectProjectHandler extends AbstractHandler
{
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException
    {
        IStructuredSelection selection = HandlerUtil.getCurrentStructuredSelection(event);
        Object selected = selection.getFirstElement();
        if (!(selected instanceof IProject project))
        {
            Activator.getDefault().getLog().log(new Status(IStatus.WARNING, Activator.PLUGIN_ID,
                message("diagnostic.no.project")));
            return null;
        }

        Job job = new Job(message("diagnostic.job.name", project.getName()))
        {
            @Override
            protected IStatus run(IProgressMonitor monitor)
            {
                if (monitor.isCanceled())
                {
                    return Status.CANCEL_STATUS;
                }

                try
                {
                    String report = inspect(project);
                    Activator.getDefault().getLog().log(new Status(IStatus.INFO, Activator.PLUGIN_ID, report));
                    return Status.OK_STATUS;
                }
                catch (Exception e)
                {
                    IStatus status = new Status(IStatus.ERROR, Activator.PLUGIN_ID, message("diagnostic.failure"), e);
                    Activator.getDefault().getLog().log(status);
                    return status;
                }
            }
        };
        job.setUser(true);
        job.schedule();
        return null;
    }

    private String inspect(IProject project) throws InvalidSyntaxException
    {
        StringBuilder report = new StringBuilder();
        append(report, "diagnostic.project", project.getName());

        IApplicationManager applicationManager = ServiceAccess.get(IApplicationManager.class);
        Optional<IApplication> defaultApplication = applicationManager.getDefaultApplication(project);
        if (defaultApplication.isEmpty())
        {
            List<IApplication> applications = applicationManager.getApplications(project);
            String names = applications.stream().map(IApplication::getName).collect(Collectors.joining(", "));
            append(report, "diagnostic.no.application");
            append(report, "diagnostic.applications", names);
            return report.toString();
        }

        IApplication application = defaultApplication.get();
        append(report, "diagnostic.application", application.getName(), application.getId());
        if (!(application instanceof IInfobaseApplication infobaseApplication))
        {
            append(report, "diagnostic.not.infobase");
            return report.toString();
        }

        InfobaseReference infobase = infobaseApplication.getInfobase();
        append(report, "diagnostic.infobase.type", infobase.getInfobaseType());
        if (!(infobase.getConnectionString() instanceof FileConnectionString fileConnectionString))
        {
            Object connection = infobase.getConnectionString();
            append(report, "diagnostic.other.connection",
                connection == null ? message("diagnostic.connection.unknown") : connection.getClass().getSimpleName());
            return report.toString();
        }
        append(report, "diagnostic.file.infobase", fileConnectionString.getFile());

        Optional<String> requiredVersion = application.getRequiredVersion().filter(version -> !version.isBlank());
        if (requiredVersion.isEmpty())
        {
            append(report, "diagnostic.no.required.version");
            return report.toString();
        }
        append(report, "diagnostic.required.version", requiredVersion.get());

        List<RuntimeInstallation> installations = getEnterpriseInstallations();
        String installationNames = installations.stream()
            .map(installation -> installation.getName() + " [" + installation.getVersionWithBuild() + "]")
            .collect(Collectors.joining(", "));
        append(report, "diagnostic.installations", installationNames);

        List<RuntimeInstallation> matches = installations.stream()
            .filter(RuntimeInstallations.filterByVersion(requiredVersion.get()))
            .collect(Collectors.toList());
        if (matches.size() != 1)
        {
            append(report, "diagnostic.no.installation");
            return report.toString();
        }

        IRuntimeComponentManager componentManager = ServiceAccess.get(IRuntimeComponentManager.class);
        ComponentExecutorInfo<ILaunchableRuntimeComponent, IDesignerSessionThickClientLauncher> componentAndExecutor =
            componentManager.resolveExecutor(ILaunchableRuntimeComponent.class,
                IDesignerSessionThickClientLauncher.class, matches.get(0), IRuntimeComponentTypes.THICK_CLIENT);
        var sessionDirectory = componentAndExecutor.getExecutor().getDesignerSessionDirectory(
            componentAndExecutor.getComponent(), infobase, null);
        if (sessionDirectory == null)
        {
            append(report, "diagnostic.session.closed");
        }
        else
        {
            append(report, "diagnostic.session.open", sessionDirectory);
        }
        return report.toString();
    }

    private List<RuntimeInstallation> getEnterpriseInstallations() throws InvalidSyntaxException
    {
        List<RuntimeInstallation> installations = new ArrayList<>();
        BundleContext context = Activator.getDefault().getBundle().getBundleContext();
        ServiceReference<?>[] references = context.getAllServiceReferences(IRuntimeInstallationManager.class.getName(), null);
        if (references == null)
        {
            return installations;
        }

        for (ServiceReference<?> reference : references)
        {
            Object service = context.getService(reference);
            try
            {
                if (service instanceof IRuntimeInstallationManager manager
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

    private void append(StringBuilder report, String key, Object... arguments)
    {
        report.append(message(key, arguments)).append(System.lineSeparator());
    }

    private String message(String key, Object... arguments)
    {
        ResourceBundle bundle = Platform.getResourceBundle(Activator.getDefault().getBundle());
        return MessageFormat.format(bundle.getString(key), arguments);
    }
}
