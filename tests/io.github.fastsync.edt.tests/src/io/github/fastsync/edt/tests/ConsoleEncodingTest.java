package io.github.fastsync.edt.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.lang.reflect.Method;

import org.eclipse.core.runtime.Platform;
import org.eclipse.ui.console.MessageConsole;
import org.junit.Test;

import org.osgi.framework.Bundle;

/**
 * The plugin writes UTF-8 bytes into its console, so the console must decode UTF-8 regardless of the
 * workbench encoding (windows-1251 on Russian Windows). ConsoleOutput lives in a non-exported package,
 * so it is loaded through the bundle.
 */
public class ConsoleEncodingTest
{
    @Test
    public void consoleIsCreatedWithUtf8Encoding() throws Exception
    {
        Bundle bundle = Platform.getBundle("io.github.fastsync.edt");
        assertNotNull(bundle);

        Class<?> consoleOutput = bundle.loadClass("io.github.fastsync.edt.ui.ConsoleOutput");
        Method newConsole = consoleOutput.getDeclaredMethod("newConsole");
        newConsole.setAccessible(true);
        MessageConsole console = (MessageConsole) newConsole.invoke(null);

        assertEquals("UTF-8", console.getEncoding());
    }
}
