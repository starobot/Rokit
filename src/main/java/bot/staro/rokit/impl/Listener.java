package bot.staro.rokit.impl;

import bot.staro.rokit.EventListener;

import java.util.function.Consumer;

public class Listener<E> implements EventListener<E> {
    private final Class<E> type;
    private final Consumer<E> consumer;
    private final int priority;

    private Listener(Class<E> type, Consumer<E> consumer, int priority) {
        this.type = type;
        this.consumer = consumer;
        this.priority = priority;
    }

    @Override
    public void invoke(E event) {
        consumer.accept(event);
    }

    @Override
    public Class<E> getType() {
        return type;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    public static <E> Builder<E> builder() {
        return new Builder<>();
    }

    public static class Builder<E> {
        private Class<E> type;
        private Consumer<E> consumer = event -> {};
        private int priority = DEFAULT_LISTENER_PRIORITY;

        public Builder<E> withType(Class<E> type) {
            this.type = type;
            return this;
        }

        public Builder<E> withConsumer(Consumer<E> consumer) {
            this.consumer = consumer;
            return this;
        }

        public Builder<E> withPriority(int priority) {
            this.priority = priority;
            return this;
        }

        public Listener<E> build() {
            if (type == null) {
                throw new IllegalStateException("Type must be defined");
            }

            return new Listener<>(type, consumer, priority);
        }
    }

}
