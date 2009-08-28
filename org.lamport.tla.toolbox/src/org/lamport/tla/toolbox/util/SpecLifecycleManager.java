package org.lamport.tla.toolbox.util;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.lamport.tla.toolbox.Activator;
import org.lamport.tla.toolbox.tool.SpecEvent;
import org.lamport.tla.toolbox.tool.SpecLifecycleParticipant;

/**
 * The spec life cycle manager is keeping track of the spec life cycle participants and is used to propagate events to them 
 * @author Simon Zambrovski
 * @version $Id$
 */
public class SpecLifecycleManager
{

    private static final String POINT = "org.lamport.tla.toolbox.spec";
    private static final String CLASS_ATTR_NAME = "class";
    private SpecLifecycleParticipant[] extensions = new SpecLifecycleParticipant[0];

    /**
     * Simple strategy calling the method
     */
    private ExtensionInvocationStrategy simpleInvocationStrategy = new ExtensionInvocationStrategy() {
        // simple strategy calling the method directly
        public boolean invoke(SpecLifecycleParticipant target, SpecEvent event)
        {
            return target.eventOccured(event);
        }
    };

    /**
     * Initializes the spec participants
     */
    public void initialize()
    {
        IExtensionRegistry extensionRegistry = Platform.getExtensionRegistry();
        if (extensionRegistry != null)
        {
            IConfigurationElement[] decls = extensionRegistry.getConfigurationElementsFor(POINT);
            extensions = new SpecLifecycleParticipant[decls.length];
            for (int i = 0; i < decls.length; i++)
            {
                try
                {
                    extensions[i] = (SpecLifecycleParticipant) decls[i].createExecutableExtension(CLASS_ATTR_NAME);
                    extensions[i].initialize();
                } catch (CoreException e)
                {
                    Activator.logError("Error retrieving the registered participants", e);
                }
            }
        }
    }

    /**
     * Sends events ignoring veto responses
     * Shortcut for {@link SpecLifecycleManager#sendEventWithVeto(SpecEvent, false)}
     */
    public boolean sendEvent(SpecEvent event)
    {
        return sendEventWithVeto(event, false);
    }

    /**
     * Sends event to the registered participants
     * @param event
     * @param stopOnVeto
     * @return
     */
    public boolean sendEventWithVeto(SpecEvent event, boolean stopOnVeto)
    {
        boolean responseAll = true;
        boolean response;
        for (int i = 0; i < extensions.length; i++)
        {
            // local response
            response = simpleInvocationStrategy.invoke(extensions[i], event);

            // global response
            responseAll = responseAll && response;

            // veto answer received, stop
            if (stopOnVeto && !response)
            {
                break;
            }
        }
        return responseAll;
    }

    /**
     * Distributes the terminate message  
     */
    public void terminate()
    {
        for (int i = 0; i < extensions.length; i++)
        {
            extensions[i].terminate();
        }
    }

    /**
     * Describers the invocation strategy
     * <br>This is the point where the indirection can be introduced for asynchronous invocation,
     * invocation with timeouts etc...  
     */
    static abstract class ExtensionInvocationStrategy
    {
        /**
         * Responsible for invocation of the eventOccured method 
         * @param target target to invoke on
         * @param event event to send
         * @return result of invocation
         */
        public abstract boolean invoke(SpecLifecycleParticipant target, SpecEvent event);
    }
}
