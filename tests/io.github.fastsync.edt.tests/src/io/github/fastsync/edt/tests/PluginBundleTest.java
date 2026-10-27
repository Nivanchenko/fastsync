package io.github.fastsync.edt.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.eclipse.core.runtime.Platform;
import org.junit.Test;

import org.osgi.framework.Bundle;

/** Basic OSGi smoke test proving the plug-in bundle is available to tests. */
public class PluginBundleTest
{
    @Test
    public void pluginBundleIsResolvable()
    {
        Bundle bundle = Platform.getBundle("io.github.fastsync.edt");
        assertNotNull(bundle);
        assertEquals("io.github.fastsync.edt", bundle.getSymbolicName());
    }
}
