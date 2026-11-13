package io.github.fastsync.edt.upload;

import java.util.List;
import java.util.Set;

/** One prepared ibcmd invocation with the indexes of arguments to mask. */
public record IbcmdInvocation(List<String> arguments, Set<Integer> secretIndexes)
{
    public IbcmdInvocation
    {
        arguments = List.copyOf(arguments);
    }

    public String display()
    {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < arguments.size(); i++)
        {
            if (i > 0)
            {
                builder.append(' ');
            }
            String argument = arguments.get(i);
            if (secretIndexes.contains(i))
            {
                int separator = argument.indexOf('=');
                builder.append(separator < 0 ? "***" : argument.substring(0, separator + 1) + "***"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            else
            {
                builder.append(argument);
            }
        }
        return builder.toString();
    }
}
