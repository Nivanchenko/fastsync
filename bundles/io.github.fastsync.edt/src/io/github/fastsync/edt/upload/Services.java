package io.github.fastsync.edt.upload;

import com._1c.g5.wiring.ServiceAccess;

/** Tolerant access to EDT services; a missing service is reported by the caller. */
final class Services
{
    private Services()
    {
    }

    static <T> T get(Class<T> type)
    {
        try
        {
            return ServiceAccess.get(type);
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }
}
