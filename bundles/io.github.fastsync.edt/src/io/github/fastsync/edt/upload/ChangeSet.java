package io.github.fastsync.edt.upload;

import java.util.Set;

import org.eclipse.emf.ecore.EObject;

/** Unsynchronized changes between the project and the infobase. */
public record ChangeSet(Set<EObject> added, Set<EObject> changed, Set<String> removed)
{
    public boolean isEmpty()
    {
        return added.isEmpty() && changed.isEmpty() && removed.isEmpty();
    }

    /**
     * A removed URI may mean both a deletion and a rename (the old XML path disappears), so
     * a non-empty removed set forces a full reload.
     */
    public boolean requiresFullReload()
    {
        return !removed.isEmpty();
    }
}
