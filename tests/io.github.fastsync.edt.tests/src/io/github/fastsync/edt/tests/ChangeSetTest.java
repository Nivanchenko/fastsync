package io.github.fastsync.edt.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.junit.Test;

import io.github.fastsync.edt.upload.ChangeSet;

public class ChangeSetTest
{
    @Test
    public void emptySetHasNothingToUpload()
    {
        ChangeSet changes = new ChangeSet(Set.of(), Set.of(), Set.of());

        assertTrue(changes.isEmpty());
        assertFalse(changes.requiresFullReload());
    }

    @Test
    public void removedObjectForcesFullReload()
    {
        ChangeSet changes = new ChangeSet(Set.of(), Set.of(), Set.of("Document/Приказ.xml"));

        assertFalse(changes.isEmpty());
        assertTrue(changes.requiresFullReload());
    }

    @Test
    public void addedObjectsAllowPartialUpload()
    {
        EObject object = org.eclipse.emf.ecore.EcoreFactory.eINSTANCE.createEObject();
        ChangeSet changes = new ChangeSet(Set.of(object), Set.of(), Set.of());

        assertFalse(changes.isEmpty());
        assertFalse(changes.requiresFullReload());
    }
}
