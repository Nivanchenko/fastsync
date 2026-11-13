package io.github.fastsync.edt.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com._1c.g5.v8.dt.core.platform.IConfigurationProvider;
import com._1c.g5.v8.dt.core.platform.IDtProject;
import com._1c.g5.v8.dt.export.IExportOperation;
import com._1c.g5.v8.dt.export.IExportOperationFactory;
import com._1c.g5.v8.dt.export.IExportPreparationService;
import com._1c.g5.v8.dt.export.IExportStrategy;
import com._1c.g5.v8.dt.metadata.mdclass.Configuration;
import com._1c.g5.v8.dt.platform.services.core.infobases.IInfobaseAccessSettings;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.ILaunchableRuntimeComponent;
import com._1c.g5.v8.dt.platform.services.model.InfobaseReference;
import com._1c.g5.v8.dt.platform.version.Version;
import com.e1c.g5.dt.applications.IApplication;

import io.github.fastsync.edt.upload.SourceExporter;
import io.github.fastsync.edt.upload.UploadTarget;

public class SourceExporterTest
{
    private Path directory;
    private IExportPreparationService preparationService;
    private IExportOperationFactory operationFactory;
    private IConfigurationProvider configurationProvider;
    private IExportOperation operation;

    @Before
    public void setUp() throws IOException
    {
        directory = Files.createTempDirectory("fastsync-exporter-");
        preparationService = mock(IExportPreparationService.class);
        operationFactory = mock(IExportOperationFactory.class);
        configurationProvider = mock(IConfigurationProvider.class);
        operation = mock(IExportOperation.class);
    }

    @After
    public void tearDown() throws IOException
    {
        try (Stream<Path> stream = Files.walk(directory))
        {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    public void exportsConfigurationAfterPreparingSources() throws Exception
    {
        IProject project = mock(IProject.class);
        Configuration configuration = mock(Configuration.class);
        Version version = new Version(8, 3, 27);

        when(configurationProvider.getConfiguration(project)).thenReturn(configuration);
        when(preparationService.waitForExportPreparation(any(), anyLong(), any())).thenReturn(Status.OK_STATUS);
        when(operationFactory.createExportOperation(any(Path.class), eq(version), any(IExportStrategy.class),
            eq(configuration))).thenReturn(operation);
        when(operation.run(any())).thenReturn(Status.OK_STATUS);

        SourceExporter.ExportResult result = new SourceExporter(preparationService, operationFactory,
            configurationProvider).exportFull(project, target(project, version), directory,
                new NullProgressMonitor());

        verify(preparationService).waitForExportPreparation(any(), anyLong(), any());
        verify(operationFactory).createExportOperation(any(Path.class), eq(version), any(IExportStrategy.class),
            eq(configuration));
        verify(operation).run(any());
        assertEquals(directory, result.directory());
        assertEquals(directory.resolve("ConfigDumpInfo.xml"), result.configDumpInfo());
        assertTrue(result.files().isEmpty());
    }

    private UploadTarget target(IProject project, Version version)
    {
        IDtProject dtProject = mock(IDtProject.class);
        return new UploadTarget(project, dtProject, mock(IApplication.class), mock(InfobaseReference.class),
            directory, "8.3.27.2342", null, mock(ILaunchableRuntimeComponent.class), Path.of("/tmp/ibcmd"),
            version, IInfobaseAccessSettings.NOT_DEFINED);
    }
}
