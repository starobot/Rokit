package bot.staro.rokit.spi;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import java.util.Collections;
import java.util.Map;

public interface ProviderAwareBinder extends ParamBinder {
    default Map<String,String> requiredProviders(ExecutableElement method, VariableElement parameter, ProcessingEnvironment environment) {
        return Collections.emptyMap();
    }

}
