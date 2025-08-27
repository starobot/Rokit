package bot.staro.rokit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A meta-annotation that marks a listener annotation as requiring
 * a 'safe' or 'contextual' check. When a method is annotated with
 * a @ContextualListener, any parameters resolved by a
 * ContextualParamBinder will be implicitly null-checked.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.ANNOTATION_TYPE)
public @interface ContextualListener {
}