package io.github.fastsync.edt.upload;

import java.nio.file.Path;

import org.eclipse.core.resources.IProject;

import com._1c.g5.v8.dt.core.platform.IDtProject;
import com._1c.g5.v8.dt.platform.services.core.infobases.IInfobaseAccessSettings;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.ILaunchableRuntimeComponent;
import com._1c.g5.v8.dt.platform.services.model.InfobaseReference;
import com._1c.g5.v8.dt.platform.services.model.RuntimeInstallation;
import com._1c.g5.v8.dt.platform.version.Version;
import com.e1c.g5.dt.applications.IApplication;

/** Fully resolved upload target; creation means every preflight check passed. */
public record UploadTarget(
    IProject project,
    IDtProject dtProject,
    IApplication application,
    InfobaseReference infobase,
    Path infobasePath,
    String requiredVersion,
    RuntimeInstallation installation,
    ILaunchableRuntimeComponent thickClient,
    Path ibcmd,
    Version configurationVersion,
    IInfobaseAccessSettings accessSettings)
{
}
