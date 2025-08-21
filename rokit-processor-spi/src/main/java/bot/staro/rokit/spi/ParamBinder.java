package bot.staro.rokit.spi;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import java.util.Set;

public interface ParamBinder {
    boolean supports(ExecutableElement method, VariableElement parameter, ProcessingEnvironment environment);

    String patternKey(ExecutableElement method, VariableElement parameter, ProcessingEnvironment environment);

    Binding plan(ExecutableElement method, VariableElement parameter, ProcessingEnvironment environment);

    record Binding(Set<String> guardBits, Set<Extractor> extractors, String argumentExpression) {}

    record Extractor(String localName, String declaredType, String initExpression) {}

}
