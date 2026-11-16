package io.github.fastsync.edt;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/** OSGi lifecycle entry point for the FastSync EDT plug-in. */
public class Activator extends AbstractUIPlugin
{
    public static final String PLUGIN_ID = "io.github.fastsync.edt";

    private static Activator plugin;

    @Override
    public void start(BundleContext context) throws Exception
    {
        super.start(context);
        plugin = this;
    }

    @Override
    public void stop(BundleContext context) throws Exception
    {
        plugin = null;
        super.stop(context);
    }

    public static Activator getDefault()
    {
        return plugin;
    }

    /** Writes a diagnostic message to the Eclipse Error Log. */
    public static void log(int severity, String message, Throwable error)
    {
        Activator activator = getDefault();
        if (activator != null)
        {
            activator.getLog().log(new Status(severity, PLUGIN_ID, message, error));
        }
    }
}
