package bot.staro.rokit.annotation;

import bot.staro.rokit.EventBus;
import bot.staro.rokit.Priority;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for marking methods as event listeners.
 * These methods will be automatically registered when their containing object
 * is subscribed to an {@link EventBus}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Listener {
    /**
     * The priority of the listener, determining its order of execution.
     *
     * @return the priority level, with higher values executed first.
     */
    Priority priority() default Priority.DEFAULT;

}