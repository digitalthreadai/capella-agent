package com.capellaagent.core.bus;

import java.util.function.Consumer;

/**
 * Interface for the agent event message bus.
 * <p>
 * The message bus enables decoupled communication between agent components.
 * Publishers fire events without knowing who will receive them, and subscribers
 * can listen for specific event types without coupling to the publisher.
 */
public interface IAgentMessageBus {

    /**
     * Publishes an event to all registered subscribers of that event type.
     *
     * @param event the event to publish; must not be null
     */
    void publish(AgentEvent event);

    /**
     * Subscribes to events of a specific type.
     * <p>
     * The consumer will be called on the publishing thread. Long-running
     * handlers should dispatch work to a background thread.
     *
     * @param eventType the class of events to subscribe to
     * @param handler   the handler to invoke when a matching event is published
     * @param <T>       the event type
     * @return a Subscription that can be disposed to stop receiving events
     */
    <T extends AgentEvent> Subscription subscribe(Class<T> eventType, Consumer<T> handler);

    /**
     * A subscription handle that can be disposed to unsubscribe from events.
     */
    interface Subscription {

        /**
         * Unsubscribes from the event bus. After calling this method,
         * the handler will no longer receive events.
         */
        void dispose();

        /**
         * Returns whether this subscription has been disposed.
         *
         * @return true if disposed
         */
        boolean isDisposed();
    }
}
