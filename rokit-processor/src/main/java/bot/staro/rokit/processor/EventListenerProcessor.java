package bot.staro.rokit.processor;

import com.google.auto.service.AutoService;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

@SuppressWarnings("unused")
@AutoService(javax.annotation.processing.Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@SupportedAnnotationTypes("*")
@SupportedOptions("rokit.generatedPackage")
public final class EventListenerProcessor extends AbstractProcessor {
    private static final String DEF_PKG = "bot.staro.rokit.generated";
    private static final String GEN_NAME = "EventListenerRegistry";
    private static final String SERVICE_PATH = "META-INF/services/bot.staro.rokit.gen.GeneratedRegistry";

    private Elements elements;
    private boolean firstBranch;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        elements = env.getElementUtils();
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment env) {
        final Set<TypeElement> listenerAnnos = new LinkedHashSet<>();
        final TypeElement builtin = elements.getTypeElement("bot.staro.rokit.Listener");
        if (builtin != null) listenerAnnos.add(builtin);

        final TypeElement marker = elements.getTypeElement("bot.staro.rokit.ListenerAnnotation");
        if (marker != null) {
            for (final Element e : env.getElementsAnnotatedWith(marker)) {
                if (e.getKind() == ElementKind.ANNOTATION_TYPE) {
                    listenerAnnos.add((TypeElement) e);
                }
            }
        }

        final Map<String, List<MethodInfo>> byClass = new LinkedHashMap<>();
        for (final TypeElement anno : listenerAnnos) {
            for (final Element e : env.getElementsAnnotatedWith(anno)) {
                if (e instanceof ExecutableElement m) {
                    final String owner = ((TypeElement) m.getEnclosingElement()).getQualifiedName().toString();
                    byClass.computeIfAbsent(owner, k -> new ArrayList<>()).add(new MethodInfo(anno, m));
                }
            }
        }

        if (byClass.isEmpty()) return false;

        try {
            writeFiles(builtin, listenerAnnos, byClass);
        } catch (IOException ex) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Rokit processor error: " + ex);
        }
        return true;
    }

    private void writeFiles(final TypeElement builtin,
                            final Set<TypeElement> listenerAnnos,
                            final Map<String, List<MethodInfo>> byClass) throws IOException {

        final String pkg = processingEnv.getOptions().getOrDefault("rokit.generatedPackage", DEF_PKG);
        try (final Writer w = processingEnv.getFiler()
                .createResource(StandardLocation.CLASS_OUTPUT, "", SERVICE_PATH)
                .openWriter()) {
            w.write(pkg + '.' + GEN_NAME);
        }

        JavaFileObject jfo;
        try {
            jfo = processingEnv.getFiler().createSourceFile(pkg + '.' + GEN_NAME);
        } catch (FilerException ex) {
            return;
        }

        final Set<String> handlers = new TreeSet<>();
        handlers.add("bot.staro.rokit.impl.DefaultListenerHandler");
        for (final TypeElement anno : listenerAnnos) {
            if (!anno.equals(builtin)) {
                handlers.add(extractHandler(anno));
            }
        }

        try (final Writer w = jfo.openWriter()) {
            w.write("package " + pkg + ";\n\n");
            w.write("import bot.staro.rokit.*;\n");
            w.write("import bot.staro.rokit.gen.GeneratedRegistry;\n");
            w.write("import java.util.*;\n");
            w.write("import java.util.concurrent.ConcurrentHashMap;\n");
            for (final String h : handlers) {
                w.write("import " + h + ";\n");
            }

            w.write("\npublic final class " + GEN_NAME + " implements GeneratedRegistry {\n");
            w.write("    public static final Map<Object, EventConsumer<?>[]> SUBSCRIBERS = new ConcurrentHashMap<>();\n\n");

            final int totalMethods = byClass.values().stream().mapToInt(List::size).sum();

            w.write("    @Override\n");
            w.write("    public void register(final ListenerRegistry bus, final Object sub) {\n");
            w.write("        final java.util.List<bot.staro.rokit.EventConsumer<?>> tmp = new java.util.ArrayList<>(" + totalMethods + ");\n");
            firstBranch = true;
            byClass.entrySet().stream()
                    .sorted((a, b) -> a.getKey().equals(b.getKey()) ? 0
                            : elements.getTypeElement(b.getKey()).getSuperclass().toString().equals(a.getKey()) ? -1 : 1)
                    .forEach(entry -> { try { emitBranch(w, entry, builtin); } catch (IOException e) { throw new RuntimeException(e); }});
            w.write("        if (tmp.isEmpty()) return;\n");
            w.write("        final bot.staro.rokit.EventConsumer<?>[] arr = tmp.toArray(new bot.staro.rokit.EventConsumer<?>[0]);\n");
            w.write("        final bot.staro.rokit.EventConsumer<?>[] prev = SUBSCRIBERS.putIfAbsent(sub, arr);\n");
            w.write("        if (prev != null) return;\n");
            w.write("        for (final bot.staro.rokit.EventConsumer<?> c : arr) bus.internalRegister(c.getEventType(), c);\n");
            w.write("    }\n\n");

            w.write("    @Override\n");
            w.write("    public void unregister(final ListenerRegistry bus, final Object sub) {\n");
            w.write("        final EventConsumer<?>[] arr = SUBSCRIBERS.remove(sub);\n");
            w.write("        if (arr != null) {\n");
            w.write("            for (final EventConsumer<?> c : arr) bus.internalUnregister(c.getEventType(), c);\n");
            w.write("        }\n");
            w.write("    }\n\n");

            final Set<String> types = new TreeSet<>();
            for (final List<MethodInfo> methods : byClass.values()) {
                for (final MethodInfo mi : methods) {
                    types.add(raw(mi.method().getParameters().getFirst().asType().toString()) + ".class");
                }
            }

            w.write("    public static final Class<?>[] EVENT_TYPES = {\n");
            for (final String t : types) {
                w.write("        " + t + ",\n");
            }

            w.write("    };\n\n");

            w.write("    @Override\n");
            w.write("    public int getEventId(final Class<?> c) {\n");
            int id = 0;
            for (final String t : types) {
                final String cls = t.substring(0, t.length() - 6);
                w.write("        " + (id == 0 ? "if" : "else if") + " (c == " + cls + ".class) return " + (id++) + ";\n");
            }

            w.write("        return -1;\n");
            w.write("    }\n\n");

            w.write("    @Override public Class<?>[] eventTypes() { return EVENT_TYPES; }\n");
            w.write("    @Override public Map<Object, bot.staro.rokit.EventConsumer<?>[]> subscribers() { return SUBSCRIBERS; }\n");
            w.write("}\n");
        }
    }

    private void emitBranch(final Writer w, final Map.Entry<String, List<MethodInfo>> entry, final TypeElement builtin) throws IOException {
        final String owner = entry.getKey();
        w.write("        " + (firstBranch ? "if" : "else if") + " (sub instanceof " + owner + " l) {\n");
        firstBranch = false;

        for (final MethodInfo mi : entry.getValue()) {
            final ExecutableElement m = mi.method();
            if (!m.getModifiers().contains(Modifier.PUBLIC)) {
                continue;
            }

            final String evtType = raw(m.getParameters().getFirst().asType().toString());
            final String name = m.getSimpleName().toString();
            final int prio = extractPriority(m, mi.annotation());

            if (mi.annotation().equals(builtin)) {
                if (m.getParameters().size() == 1) {
                    w.write("""
                {
                    final EventConsumer<%1$s> c = new EventConsumer<>() {
                        @Override public void accept(%1$s e) { l.%2$s(e); }
                        @Override public int getPriority() { return %3$d; }
                        @Override public Class<%1$s> getEventType() { return %1$s.class; }
                    };
                    tmp.add(c);
                }
                """.formatted(evtType, name, prio));
                } else {
                    final int extras = m.getParameters().size() - 1;
                    final StringBuilder guardDecls = new StringBuilder();
                    final StringBuilder callArgs = new StringBuilder();
                    for (int i = 1; i < m.getParameters().size(); i++) {
                        final String pt = raw(m.getParameters().get(i).asType().toString());
                        final String var = "a" + (i - 1);
                        guardDecls.append("                            final Object ").append(var).append(" = w[").append(i - 1).append("];\n");
                        guardDecls.append("                            if (").append(var).append(" != null && !(").append(var).append(" instanceof ").append(pt).append(")) return;\n");
                        callArgs.append(", (").append(pt).append(") ").append(var);
                    }

                    w.write("""
                {
                    final EventConsumer<%1$s> c = new EventConsumer<>() {
                        final EventWrapper<%1$s> w0 = bus.getWrapper(%1$s.class);
                        final Object[] w = new Object[%2$d];
                        @Override public void accept(%1$s e) {
                            if (w0 == null) return;
                            w0.wrapInto(e, w);
%3$s
                            l.%4$s(e%5$s);
                        }
                        @Override public int getPriority() { return %6$d; }
                        @Override public Class<%1$s> getEventType() { return %1$s.class; }
                    };
                    tmp.add(c);
                }
                """.formatted(evtType, extras, guardDecls.toString(), name, callArgs.toString(), prio));
                }
                continue;
            }

            AnnotationMirror annoOnMethod = null;
            for (final AnnotationMirror am : m.getAnnotationMirrors()) {
                if (am.getAnnotationType().asElement().equals(mi.annotation())) {
                    annoOnMethod = am;
                    break;
                }
            }

            final List<String> injectTypes = (annoOnMethod == null) ? List.of() : extractClassArrayAttr(annoOnMethod, "injectTypes");
            final List<String> injectProviders = (annoOnMethod == null) ? List.of() : extractClassArrayAttr(annoOnMethod, "injectProviders");
            final List<String> customProviders = (annoOnMethod == null) ? List.of() : extractClassArrayAttr(annoOnMethod, "providers");

            if (injectTypes.size() != injectProviders.size()) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "injectTypes.length must equal injectProviders.length", m);
                continue;
            }

            final Map<String,String> injectMap = new HashMap<>();
            for (int i = 0; i < injectTypes.size(); i++) {
                injectMap.put(injectTypes.get(i), injectProviders.get(i));
            }

            final int paramCount = m.getParameters().size();
            final int totalExtras = Math.max(0, paramCount - 1);
            int wrapped = 0;
            for (int i = 1; i < paramCount; i++) {
                final String pt = raw(m.getParameters().get(i).asType().toString());
                if (injectMap.containsKey(pt)) {
                    break;
                }

                wrapped++;
            }

            final int nonWrapped = totalExtras - wrapped;
            final List<String> provExpr = new ArrayList<>(nonWrapped);
            int customIdx = 0;
            for (int i = 1 + wrapped; i < paramCount; i++) {
                final String pt = raw(m.getParameters().get(i).asType().toString());
                final String mapped = injectMap.get(pt);
                if (mapped != null) {
                    provExpr.add("new " + mapped + "()");
                } else {
                    if (customIdx >= customProviders.size()) {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                                "Missing ArgProvider for custom parameter type: " + pt, m);
                        provExpr.clear();
                        break;
                    }
                    provExpr.add("new " + customProviders.get(customIdx++) + "()");
                }
            }

            if (provExpr.isEmpty() && nonWrapped > 0) continue; // error reported
            if (customIdx < customProviders.size()) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Too many providers specified; " + customProviders.size()
                                + " provided but only " + customIdx + " used.", m);
                continue;
            }

            w.write("{\n");
            if (nonWrapped == 0) {
                w.write("    final bot.staro.rokit.ArgProvider<" + evtType + ">[] prov = new bot.staro.rokit.ArgProvider[0];\n");
            } else {
                w.write("    final bot.staro.rokit.ArgProvider<" + evtType + ">[] prov = new bot.staro.rokit.ArgProvider[] {\n");
                for (int i = 0; i < provExpr.size(); i++) {
                    w.write("        " + provExpr.get(i) + (i + 1 == provExpr.size() ? "\n" : ",\n"));
                }
                w.write("    };\n");
            }

            final String handlerSimple = extractHandler(mi.annotation()).replaceFirst(".+\\.", "");
            final StringBuilder guardDeclsWrapped = new StringBuilder();
            final StringBuilder guardDeclsProvided = new StringBuilder();
            final StringBuilder callArgs = new StringBuilder();

            for (int i = 0; i < wrapped; i++) {
                final String pt = raw(m.getParameters().get(1 + i).asType().toString());
                final String var = "w" + i;
                guardDeclsWrapped.append("                        final Object ").append(var).append(" = wrapped[").append(i).append("];\n");
                guardDeclsWrapped.append("                        if (").append(var).append(" != null && !(").append(var).append(" instanceof ").append(pt).append(")) return;\n");
                callArgs.append(", (").append(pt).append(") ").append(var);
            }

            for (int i = 0; i < nonWrapped; i++) {
                final String pt = raw(m.getParameters().get(1 + wrapped + i).asType().toString());
                final String var = "p" + i;
                guardDeclsProvided.append("                        final Object ").append(var).append(" = provided[").append(i).append("];\n");
                guardDeclsProvided.append("                        if (").append(var).append(" != null && !(").append(var).append(" instanceof ").append(pt).append(")) return;\n");
                callArgs.append(", (").append(pt).append(") ").append(var);
            }

            w.write("""
                final bot.staro.rokit.Invoker<%1$s> inv = new bot.staro.rokit.Invoker<%1$s>() {
                    @Override
                    public void call(final Object listener, final %1$s e, final Object[] wrapped, final Object[] provided) {
                        final %2$s l0 = (%2$s) listener;
%3$s%4$s
                        l0.%5$s(e%6$s);
                    }
                };
                @SuppressWarnings("unchecked")
                final EventConsumer<%1$s> c = (EventConsumer<%1$s>) new %7$s().createConsumer(bus, l, inv, %8$d, %1$s.class, %9$d, prov);
                tmp.add(c);
            }
            """.formatted(evtType,
                    owner,
                    guardDeclsWrapped.toString(),
                    guardDeclsProvided.toString(),
                    name,
                    callArgs.toString(),
                    handlerSimple,
                    prio,
                    wrapped));
        }

        w.write("        }\n");
    }

    private String extractHandler(final TypeElement annotation) {
        final TypeElement marker = elements.getTypeElement("bot.staro.rokit.ListenerAnnotation");
        for (final AnnotationMirror am : annotation.getAnnotationMirrors()) {
            if (am.getAnnotationType().asElement().equals(marker)) {
                for (final var ev : elements.getElementValuesWithDefaults(am).entrySet()) {
                    if ("handler".contentEquals(ev.getKey().getSimpleName())) {
                        return ev.getValue().getValue().toString();
                    }
                }
            }
        }

        return "bot.staro.rokit.impl.DefaultListenerHandler";
    }

    private static int extractPriority(final ExecutableElement element, final TypeElement annotation) {
        for (final AnnotationMirror am : element.getAnnotationMirrors()) {
            if (am.getAnnotationType().asElement().equals(annotation)) {
                for (final var ev : am.getElementValues().entrySet()) {
                    if ("priority".contentEquals(ev.getKey().getSimpleName())) {
                        return Integer.parseInt(ev.getValue().getValue().toString());
                    }
                }
            }
        }

        return 0;
    }

    private List<String> extractClassArrayAttr(final AnnotationMirror am, final String name) {
        final List<String> r = new ArrayList<>();
        for (final var ev : elements.getElementValuesWithDefaults(am).entrySet()) {
            if (name.contentEquals(ev.getKey().getSimpleName())) {
                final String s = ev.getValue().getValue().toString();
                if (s.startsWith("[") && s.endsWith("]")) {
                    final String body = s.substring(1, s.length() - 1).trim();
                    if (!body.isEmpty()) {
                        for (final String e : body.split(",")) {
                            r.add(stripClassSuffix(e.trim()));
                        }
                    }
                } else if (!s.isEmpty()) {
                    r.add(stripClassSuffix(s.trim()));
                }
            }
        }

        return r;
    }

    private static String stripClassSuffix(final String s) {
        return s.endsWith(".class") ? s.substring(0, s.length() - 6) : s;
    }

    private static String raw(final String fqn) {
        final int idx = fqn.indexOf('<');
        return idx < 0 ? fqn : fqn.substring(0, idx);
    }

    private record MethodInfo(TypeElement annotation, ExecutableElement method) {}

}
