package bot.staro.rokit.processor.binders;

import bot.staro.rokit.spi.ParamBinder;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class GenericPayloadBinder implements ParamBinder {
    private static final String[] CANDIDATE_ACCESSORS = new String[] {
            "getObject", "getPayload", "payload", "get", "value", "getValue", "object", "data", "packet", "getPacket"
    };

    @Override
    public boolean supports(final ExecutableElement method, final VariableElement parameter, final ProcessingEnvironment environment) {
        if (method.getParameters().isEmpty()) {
            return false;
        }

        final Types types = environment.getTypeUtils();
        final Elements elements = environment.getElementUtils();

        final VariableElement eventParam = method.getParameters().getFirst();
        final TypeMirror eventType = eventParam.asType();
        if (!(eventType instanceof DeclaredType)) {
            return false;
        }

        final TypeMirror wanted = parameter.asType();
        final TypeElement eventElem = (TypeElement) ((DeclaredType) eventType).asElement();

        final ExecutableElement accessor = findMatchingAccessor(eventElem, wanted, types, elements);
        return accessor != null;
    }

    @Override
    public String patternKey(final ExecutableElement method, final VariableElement parameter, final ProcessingEnvironment environment) {
        return "GENERIC_PAYLOAD";
    }

    @Override
    public Binding plan(final ExecutableElement method, final VariableElement parameter, final ProcessingEnvironment environment) {
        final Types types = environment.getTypeUtils();
        final Elements elements = environment.getElementUtils();

        final VariableElement eventParam = method.getParameters().getFirst();
        final TypeMirror eventType = eventParam.asType();
        final TypeElement eventElem = (TypeElement) ((DeclaredType) eventType).asElement();

        final ExecutableElement accessor = findMatchingAccessor(eventElem, parameter.asType(), types, elements);
        assert accessor != null;
        final String accessorCall = "event." + accessor.getSimpleName().toString() + "()";

        final String declared = parameter.asType().toString();
        final String local = localVarName(declared);

        final Extractor ex = new Extractor(local, declared, accessorCall);
        return new Binding(Collections.emptySet(), Set.of(ex), local);
    }

    private static ExecutableElement findMatchingAccessor(final TypeElement eventElem, final TypeMirror wanted, final Types types, final Elements elements) {
        final List<? extends Element> members = elements.getAllMembers(eventElem);
        for (final String name : CANDIDATE_ACCESSORS) {
            for (final Element e : members) {
                if (!(e instanceof ExecutableElement m)) {
                    continue;
                }

                if (!m.getSimpleName().contentEquals(name)) {
                    continue;
                }

                if (!m.getModifiers().contains(Modifier.PUBLIC)) {
                    continue;
                }

                if (!m.getParameters().isEmpty()) {
                    continue;
                }

                final TypeMirror ret = m.getReturnType();
                if (isAssignableConsideringTypeVars(ret, wanted, types)) {
                    return m;
                }
            }
        }

        return null;
    }

    private static boolean isAssignableConsideringTypeVars(final TypeMirror ret, final TypeMirror wanted, final Types types) {
        if (types.isAssignable(ret, wanted)) {
            return true;
        }

        if (ret instanceof TypeVariable) {
            final TypeMirror ub = ((TypeVariable) ret).getUpperBound();
            return types.isAssignable(ub, wanted);
        }

        return false;
    }

    private static String localVarName(final String fqn) {
        final int idx = fqn.lastIndexOf('.');
        final String base = idx < 0 ? fqn : fqn.substring(idx + 1);
        final String s = base.replaceAll("[^A-Za-z0-9]+", "_");
        final char c = Character.toLowerCase(s.charAt(0));
        if (s.length() == 1) {
            return String.valueOf(c);
        }

        return c + s.substring(1);
    }

}
