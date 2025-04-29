package bot.staro.rokit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an annotation as “this is a listener annotation” and
 * points at the handler class that will generate its EventConsumer.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.ANNOTATION_TYPE)
public @interface ListenerAnnotation {
    Class<? extends AnnotationHandler> handler();

}
