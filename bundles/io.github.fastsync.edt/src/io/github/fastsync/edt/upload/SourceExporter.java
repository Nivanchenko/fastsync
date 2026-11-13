package io.github.fastsync.edt.upload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.emf.ecore.EObject;

import com._1c.g5.v8.dt.core.platform.IConfigurationProvider;
import com._1c.g5.v8.dt.core.platform.IDtProject;
import com._1c.g5.v8.dt.export.IExportArtifactBuilder;
import com._1c.g5.v8.dt.export.IExportOperation;
import com._1c.g5.v8.dt.export.IExportOperationFactory;
import com._1c.g5.v8.dt.export.IExportPreparationService;
import com._1c.g5.v8.dt.export.IExportService;
import com._1c.g5.v8.dt.export.IExportServiceRegistry;
import com._1c.g5.v8.dt.export.IExportStrategy;
import com._1c.g5.v8.dt.export.OrdinaryDirectoryBuilder;
import com._1c.g5.v8.dt.metadata.mdclass.Configuration;

/** Converts project sources to the XML format accepted by ibcmd. */
public final class SourceExporter
{
    /** Exports the whole configuration or the changed objects, never touching project sources. */
    public record ExportResult(Path directory, Path configDumpInfo, List<Path> files)
    {
    }

    private static final long PREPARATION_TIMEOUT_MILLIS = 600_000L;
    private static final String EXPORT_SERVICE_EXTENSION = "com._1c.g5.v8.dt.export.exportService"; //$NON-NLS-1$

    private static final IExportStrategy EXPORT_STRATEGY = new IExportStrategy()
    {
        @Override
        public boolean exportSubordinatesObjects(EObject object)
        {
            return true;
        }

        @Override
        public boolean exportExternalProperties(EObject object)
        {
            return true;
        }

        @Override
        public boolean exportUnknown()
        {
            return false;
        }
    };

    private final IExportPreparationService preparationService;
    private final IExportOperationFactory operationFactory;
    private final IConfigurationProvider configurationProvider;

    public SourceExporter()
    {
        this(Services.get(IExportPreparationService.class), Services.get(IExportOperationFactory.class),
            Services.get(IConfigurationProvider.class));
    }

    public SourceExporter(IExportPreparationService preparationService, IExportOperationFactory operationFactory,
        IConfigurationProvider configurationProvider)
    {
        this.preparationService = preparationService;
        this.operationFactory = operationFactory;
        this.configurationProvider = configurationProvider;
    }

    public ExportResult exportFull(IProject project, UploadTarget target, Path outputDirectory,
        IProgressMonitor monitor) throws UploadException
    {
        Configuration configuration = configuration(project);
        return export(target, outputDirectory, monitor, configuration);
    }

    public ExportResult exportPartial(IProject project, UploadTarget target, ChangeSet changes, Path outputDirectory,
        IProgressMonitor monitor) throws UploadException
    {
        List<EObject> objects = new ArrayList<>(changes.added());
        objects.addAll(changes.changed());
        if (objects.isEmpty())
        {
            throw new UploadException("report.nothing"); //$NON-NLS-1$
        }
        return export(target, outputDirectory, monitor, objects.toArray(new EObject[0]));
    }

    private ExportResult export(UploadTarget target, Path outputDirectory, IProgressMonitor monitor,
        EObject... objects) throws UploadException
    {
        if (preparationService == null)
        {
            throw new UploadException("error.service", "IExportPreparationService"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        IDtProject dtProject = target.dtProject();
        IStatus preparationStatus = preparationService.waitForExportPreparation(dtProject,
            PREPARATION_TIMEOUT_MILLIS, monitor);
        if (preparationStatus != null && !preparationStatus.isOK())
        {
            throw new UploadException("error.export", preparationStatus.getMessage()); //$NON-NLS-1$
        }
        try
        {
            Files.createDirectories(outputDirectory);
        }
        catch (IOException e)
        {
            throw new UploadException(e, "error.export", e.getMessage()); //$NON-NLS-1$
        }

        if (operationFactory != null)
        {
            IExportOperation operation = operationFactory.createExportOperation(outputDirectory,
                target.configurationVersion(), EXPORT_STRATEGY, objects);
            IStatus exportStatus = operation.run(monitor);
            if (exportStatus != null && !exportStatus.isOK())
            {
                throw new UploadException("error.export", exportStatus.getMessage()); //$NON-NLS-1$
            }
        }
        else
        {
            // IExportOperationFactory is a Guice binding, not always an OSGi service; use the
            // public exportService extension point instead.
            exportWithService(target, outputDirectory, monitor, objects);
        }

        Path configDumpInfo = outputDirectory.resolve("ConfigDumpInfo.xml"); //$NON-NLS-1$
        List<Path> files = xmlFiles(outputDirectory);
        return new ExportResult(outputDirectory, configDumpInfo, files);
    }

    private void exportWithService(UploadTarget target, Path outputDirectory, IProgressMonitor monitor,
        EObject... objects) throws UploadException
    {
        IExportService service = exportService(target);
        try (IExportArtifactBuilder builder = OrdinaryDirectoryBuilder.create(outputDirectory))
        {
            for (EObject object : objects)
            {
                IStatus status = service.work(object, builder, true, true, monitor);
                if (status != null && !status.isOK())
                {
                    throw new UploadException("error.export", status.getMessage()); //$NON-NLS-1$
                }
            }
        }
        catch (IOException e)
        {
            throw new UploadException(e, "error.export", e.getMessage()); //$NON-NLS-1$
        }
    }

    private IExportService exportService(UploadTarget target) throws UploadException
    {
        IExportServiceRegistry registry = Services.get(IExportServiceRegistry.class);
        if (registry != null)
        {
            try
            {
                IExportService service = registry.getExportService(target.configurationVersion());
                if (service != null)
                {
                    return service;
                }
            }
            catch (Exception e)
            {
                // Fall through to the extension point.
            }
        }
        IExtensionRegistry extensionRegistry = Platform.getExtensionRegistry();
        if (extensionRegistry != null)
        {
            CoreException failure = null;
            for (IConfigurationElement element : extensionRegistry.getConfigurationElementsFor(EXPORT_SERVICE_EXTENSION))
            {
                try
                {
                    Object candidate = element.createExecutableExtension("class"); //$NON-NLS-1$
                    if (candidate instanceof IExportService service)
                    {
                        return service;
                    }
                }
                catch (CoreException e)
                {
                    failure = e;
                }
            }
            if (failure != null)
            {
                throw new UploadException(failure, "error.export", failure.getMessage()); //$NON-NLS-1$
            }
        }
        throw new UploadException("error.service", "IExportService"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private Configuration configuration(IProject project) throws UploadException
    {
        if (configurationProvider == null)
        {
            throw new UploadException("error.service", "IConfigurationProvider"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        Configuration configuration = configurationProvider.getConfiguration(project);
        if (configuration == null)
        {
            throw new UploadException("error.export", project.getName()); //$NON-NLS-1$
        }
        return configuration;
    }

    private List<Path> xmlFiles(Path directory)
    {
        try (Stream<Path> stream = Files.walk(directory))
        {
            return stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".xml")) //$NON-NLS-1$
                .filter(path -> !path.getFileName().toString().equals("ConfigDumpInfo.xml")) //$NON-NLS-1$
                .sorted(Comparator.comparing(Path::toString))
                .toList();
        }
        catch (IOException e)
        {
            return List.of();
        }
    }
}
