package com.capellaagent.core.bus;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple in-memory implementation of the agent message bus.
 * <p>
 * Uses {@link CopyOnWriteArrayList} for thread-safe listener management.
 * Event dispatch is synchronous on the publishing thread. Handlers that
 * throw exceptions are logged but do not prevent other handlers from executing.
 * <p>
 * This is a singleton; obtain the instance via {@link #getInstance()}.
 */
public final class AgentMessageBus implements IAgentMessageBus {

    private static final Logger LOG = Logger.getLogger(AgentMessageBus.class.getName());

    private static final AgentMessageBus INSTANCE = new AgentMessageBus();

    private final CopyOnWriteArrayList<EventListener<?>> listeners = new CopyOnWriteArrayList<>();

    private AgentMessageBus() {
        // Singleton
    }

    /**
     * Returns the singleton instance of the message bus.
     *
     * @return the message bus instance
     */
    public static AgentMessageBus getInstance() {
        return INSTANCE;
    }

    @Override
    public void publish(AgentEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }

        LOG.fine("Publishing event: " + event.getClass().getSimpleName() +
                " from " + event.getSource());

        for (EventListener<?> listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                LOG.log(Level.WARNING,
                        "Event handler threw exception for " + event.getClass().getSimpleName(), e);
            }
        }
    }

    @Override
    public <T extends AgentEvent> Subscription subscribe(Class<T> eventType, Consumer<T> handler) {
        if (eventType == null) {
            throw new IllegalArgumentException("eventType must not be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler must not be null");
        }

        EventListener<T> listener = new EventListener<>(eventType, handler);
        listeners.add(listener);

        LOG.fine("Subscribed to " + eventType.getSimpleName() +
                " (total listeners: " + listeners.size() + ")");

        return new SubscriptionImpl(listener);
    }

    /**
     * Returns the current number of active subscriptions.
     *
     * @return the listener count
     */
    public int getListenerCount() {
        return listeners.size();
    }

    /**
     * Removes all subscriptions. Intended for testing only.
     */
    public void clear() {
        listeners.clear();
    }

    /**
     * Internal listener wrapper that type-checks and dispatches events.
     */
    private static final class EventListener<T extends AgentEvent> {

        private final Class<T> eventType;
        private final Consumer<T> handler;

        EventListener(Class<T> eventType, Consumer<T> handler) {
            this.eventType = eventType;
            this.handler = handler;
        }

        @SuppressWarnings("unchecked")
        void onEvent(AgentEvent event) {
            if (eventType.isInstance(event)) {
                handler.accept((T) event);
            }
        }
    }

    /**
     * Internal subscription implementation.
     */
    private final class SubscriptionImpl implements Subscription {

        private final EventListener<?> listener;
        private final AtomicBoolean disposed = new AtomicBoolean(false);

        SubscriptionImpl(EventListener<?> listener) {
            this.listener = listener;
        }

        @Override
        public void dispose() {
            if (disposed.compareAndSet(false, true)) {
                listeners.remove(listener);
                LOG.fine("Subscription disposed (remaining listeners: " + listeners.size() + ")");
            }
        }

        @Override
        public boolean isDisposed() {
            return disposed.get();
        }
    }
}
