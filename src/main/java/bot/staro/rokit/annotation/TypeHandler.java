package bot.staro.rokit.annotation;

import bot.staro.rokit.EventWrapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TypeHandler {
    Class<? extends EventWrapper> value();

}
