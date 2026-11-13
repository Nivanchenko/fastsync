package io.github.fastsync.edt.tests;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.github.fastsync.edt.upload.IbcmdInvocation;
import io.github.fastsync.edt.upload.IbcmdRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class IbcmdRunnerTest
{
    private Path directory;

    @Before
    public void setUp() throws IOException
    {
        directory = Files.createTempDirectory("fastsync-runner-");
    }

    @After
    public void tearDown() throws IOException
    {
        try (var stream = Files.walk(directory))
        {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    public void streamsOutputAndReturnsExitCode() throws Exception
    {
        Path script = script("#!/bin/sh\necho hello\necho warning >&2\nexit 3\n");
        List<String> lines = new ArrayList<>();

        IbcmdRunner.Result result = new IbcmdRunner().run(
            new IbcmdInvocation(List.of(script.toString()), Set.of()), new NullProgressMonitor(), lines::add);

        assertEquals(3, result.exitCode());
        assertTrue(result.output().contains("hello"));
        assertTrue(result.output().contains("warning"));
        assertTrue(lines.contains("hello"));
    }

    @Test
    public void masksSecretArgumentsInStreamedOutput() throws Exception
    {
        Path script = script("#!/bin/sh\necho \"$@\"\n");
        List<String> lines = new ArrayList<>();

        new IbcmdRunner().run(new IbcmdInvocation(List.of(script.toString(), "--password=secret"),
            Set.of(1)), new NullProgressMonitor(), lines::add);

        for (String line : lines)
        {
            assertTrue(!line.contains("secret"));
        }
    }

    @Test
    public void runningProcessIsNotCancelled() throws Exception
    {
        Path script = script("#!/bin/sh\nexit 0\n");

        IbcmdRunner.Result result = new IbcmdRunner().run(
            new IbcmdInvocation(List.of(script.toString()), Set.of()), new NullProgressMonitor(), line -> {
            });

        assertEquals(0, result.exitCode());
        assertTrue(!result.cancelled());
    }

    private Path script(String content) throws IOException
    {
        Path script = directory.resolve("ibcmd-stub.sh");
        Files.writeString(script, content, StandardCharsets.UTF_8);
        assertTrue(script.toFile().setExecutable(true));
        return script;
    }
}
