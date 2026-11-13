package io.github.fastsync.edt.upload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.emf.ecore.EObject;

import com._1c.g5.v8.dt.core.platform.IConfigurationProvider;
import com._1c.g5.v8.dt.core.platform.IDtProject;
import com._1c.g5.v8.dt.export.IExportOperation;
import com._1c.g5.v8.dt.export.IExportOperationFactory;
import com._1c.g5.v8.dt.export.IExportPreparationService;
import com._1c.g5.v8.dt.export.IExportStrategy;
import com._1c.g5.v8.dt.metadata.mdclass.Configuration;
import com._1c.g5.wiring.ServiceAccess;

/** Converts project sources to the XML format accepted by ibcmd. */
public final class SourceExporter
{
    /** Exports the whole configuration or the changed objects, never touching project sources. */
    public record ExportResult(Path directory, Path configDumpInfo, List<Path> files)
    {
    }

    private static final long PREPARATION_TIMEOUT_MILLIS = 600_000L;

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
        this(ServiceAccess.get(IExportPreparationService.class), ServiceAccess.get(IExportOperationFactory.class),
            ServiceAccess.get(IConfigurationProvider.class));
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
        if (operationFactory == null)
        {
            throw new UploadException("error.service", "IExportOperationFactory"); //$NON-NLS-1$ //$NON-NLS-2$
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

        IExportOperation operation = operationFactory.createExportOperation(outputDirectory,
            target.configurationVersion(), EXPORT_STRATEGY, objects);
        IStatus exportStatus = operation.run(monitor);
        if (exportStatus != null && !exportStatus.isOK())
        {
            throw new UploadException("error.export", exportStatus.getMessage()); //$NON-NLS-1$
        }

        Path configDumpInfo = outputDirectory.resolve("ConfigDumpInfo.xml"); //$NON-NLS-1$
        List<Path> files = xmlFiles(outputDirectory);
        return new ExportResult(outputDirectory, configDumpInfo, files);
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
