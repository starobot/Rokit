// Replace this file in the rokit-processor module
package bot.staro.rokit.processor.binders;

import bot.staro.rokit.spi.ParamBinder;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.Collections;
import java.util.Set;

public final class GenericPayloadBinder implements ParamBinder {
    private static final String[] CANDIDATE_ACCESSORS = new String[] {
            "getPacket", "getPayload", "getObject", "payload", "get", "value", "getValue", "object", "data"
    };

    @Override
    public boolean supports(final ExecutableElement method, final VariableElement parameter, final ProcessingEnvironment environment) {
        if (method.getParameters().isEmpty()) {
            return false;
        }

        final VariableElement eventParam = method.getParameters().getFirst();
        final TypeMirror eventType = eventParam.asType();
        if (!(eventType instanceof DeclaredType declaredEventType)) {
            return false;
        }

        if (declaredEventType.getTypeArguments().isEmpty()) {
            return false;
        }

        final Types types = environment.getTypeUtils();
        final TypeMirror genericTypeArgument = declaredEventType.getTypeArguments().getFirst();
        final TypeMirror wantedParamType = parameter.asType();

        return types.isAssignable(types.erasure(genericTypeArgument), types.erasure(wantedParamType));
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

        final ExecutableElement accessor = findAccessor(eventElem, parameter.asType(), types, elements);
        if (accessor == null) {
            throw new IllegalStateException("GenericPayloadBinder supports this parameter but could not find a suitable public accessor method on the event " + eventElem.getQualifiedName());
        }

        final String accessorCall = "event." + accessor.getSimpleName().toString() + "()";
        final String declared = parameter.asType().toString();
        final String local = localVarName(declared);

        final Extractor ex = new Extractor(local, declared, accessorCall);
        return new Binding(Collections.emptySet(), Set.of(ex), local);
    }

    private static ExecutableElement findAccessor(final TypeElement eventElem, final TypeMirror wanted, final Types types, final Elements elements) {
        for (final Element member : elements.getAllMembers(eventElem)) {
            if (member.getKind() != ElementKind.METHOD || !(member instanceof ExecutableElement accessor)) {
                continue;
            }

            if (!isCandidateName(accessor.getSimpleName().toString()) || !accessor.getModifiers().contains(Modifier.PUBLIC) || !accessor.getParameters().isEmpty()) {
                continue;
            }

            if (types.isAssignable(types.erasure(accessor.getReturnType()), types.erasure(wanted))) {
                return accessor;
            }
        }

        return null;
    }

    private static boolean isCandidateName(final String name) {
        for (final String candidate : CANDIDATE_ACCESSORS) {
            if (candidate.equals(name)) {
                return true;
            }
        }

        return false;
    }

    private static String localVarName(final String fqn) {
        final int idx = fqn.lastIndexOf('.');
        final String base = idx < 0 ? fqn : fqn.substring(idx + 1);
        final String s = base.replaceAll("[^A-Za-z0-9]+", "_");
        final char c = Character.toLowerCase(s.charAt(0));
        return s.length() == 1 ? String.valueOf(c) : c + s.substring(1);
    }

}