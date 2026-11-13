package io.github.fastsync.edt.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;

import com._1c.g5.v8.dt.platform.services.core.runtimes.RuntimeInstallations;
import com._1c.g5.v8.dt.platform.services.model.RuntimeInstallation;

public class RuntimeInstallationsTest
{
    @Test
    public void versionFilterMatchesTheRequiredBuild()
    {
        RuntimeInstallation exactVersion = installation("8.3.27.2342");

        assertTrue(RuntimeInstallations.filterByVersion("8.3.27.2342").test(exactVersion));
    }

    @Test
    public void versionFilterRejectsAnotherBuild()
    {
        assertFalse(RuntimeInstallations.filterByVersion("8.3.27.2342").test(installation("8.3.27.2018")));
    }

    private RuntimeInstallation installation(String version)
    {
        RuntimeInstallation installation = mock(RuntimeInstallation.class);
        when(installation.getVersionWithBuild()).thenReturn(version);
        return installation;
    }
}
