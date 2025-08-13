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
        if (builtin != null) {
            listenerAnnos.add(builtin);
        }

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
                    String owner = ((TypeElement) m.getEnclosingElement()).getQualifiedName().toString();
                    byClass.computeIfAbsent(owner, k -> new java.util.ArrayList<>()).add(new MethodInfo(anno, m));
                }
            }
        }

        if (byClass.isEmpty()) {
            return false;
        }

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
            w.write("import java.lang.reflect.Method;\n");
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
                    final StringBuilder args = new StringBuilder();
                    for (int i = 1; i < m.getParameters().size(); i++) {
                        final String pt = raw(m.getParameters().get(i).asType().toString());
                        if (!args.isEmpty()) {
                            args.append(", ");
                        }
                        args.append("(").append(pt).append(") w[").append(i - 1).append("]");
                    }
                    w.write("""
                {
                    final EventConsumer<%1$s> c = new EventConsumer<>() {
                        final EventWrapper<%1$s> w0 = bus.getWrapper(%1$s.class);
                        final Object[] w = new Object[%2$d];
                        @Override public void accept(%1$s e) {
                            if (w0 == null) return;
                            w0.wrapInto(e, w);
                            l.%3$s(e%4$s);
                        }
                        @Override public int getPriority() { return %5$d; }
                        @Override public Class<%1$s> getEventType() { return %1$s.class; }
                    };
                    tmp.add(c);
                }
                """.formatted(evtType, extras, name, args.isEmpty() ? "" : ", " + args, prio));
                }
            } else {
                final String handlerSimple = extractHandler(mi.annotation()).replaceFirst(".+\\.", "");
                w.write("""
            {
                final Object h = new %1$s();
                if (h instanceof bot.staro.rokit.MethodAnnotationHandler mh) {
                    final java.lang.reflect.Method mtd;
                    try {
                        mtd = %2$s.class.getMethod("%3$s", %4$s);
                    } catch (Throwable ex) { throw new RuntimeException(ex); }
                    @SuppressWarnings("unchecked")
                    final EventConsumer<%5$s> c = (EventConsumer<%5$s>) mh.createConsumer(bus, l, mtd, %6$d, %5$s.class);
                    tmp.add(c);
                } else {
            """.formatted(handlerSimple, owner, name, paramClassList(m), evtType, prio));

                AnnotationMirror annoOnMethod = null;
                for (final AnnotationMirror am : m.getAnnotationMirrors()) {
                    if (am.getAnnotationType().asElement().equals(mi.annotation())) {
                        annoOnMethod = am;
                        break;
                    }
                }

                final Integer wrappedAttr = annoOnMethod == null ? null : extractIntAttr(annoOnMethod, "wrapped");
                final List<String> providersAttr = annoOnMethod == null ? List.of() : extractClassArrayAttr(annoOnMethod, "providers");
                final int paramCount = m.getParameters().size();
                final int wrapped = wrappedAttr == null ? 0 : wrappedAttr;
                final int nonWrapped = Math.max(0, paramCount - 1 - wrapped);

                if (providersAttr.size() != nonWrapped) {
                    w.write("                    }\n");
                    w.write("                }\n");
                    continue;
                }

                w.write("{\n");
                if (providersAttr.isEmpty()) {
                    w.write("    final bot.staro.rokit.ArgProvider<" + evtType + ">[] prov = new bot.staro.rokit.ArgProvider[0];\n");
                } else {
                    w.write("    final bot.staro.rokit.ArgProvider<" + evtType + ">[] prov = new bot.staro.rokit.ArgProvider[] {\n");
                    for (int i = 0; i < providersAttr.size(); i++) {
                        final String provCls = providersAttr.get(i);
                        w.write("        new " + provCls + "()" + (i == providersAttr.size() - 1 ? "\n" : ",\n"));
                    }
                    w.write("    };\n");
                }

                w.write("""
                final bot.staro.rokit.Invoker<%1$s> inv = new bot.staro.rokit.Invoker<%1$s>() {
                    @Override
                    public void call(final Object listener, final %1$s e, final Object[] wrapped, final Object[] provided) {
                        final %2$s l0 = (%2$s) listener;
            """.formatted(evtType, owner));
                w.write("                        l0." + name + "(e");
                for (int i = 0; i < wrapped; i++) {
                    final String pt = raw(m.getParameters().get(1 + i).asType().toString());
                    w.write(", (" + pt + ") wrapped[" + i + "]");
                }

                for (int i = 0; i < nonWrapped; i++) {
                    final String pt = raw(m.getParameters().get(1 + wrapped + i).asType().toString());
                    w.write(", (" + pt + ") provided[" + i + "]");
                }

                w.write(");\n");
                w.write("""
                    }
                };
                @SuppressWarnings("unchecked")
                final EventConsumer<%1$s> c = (EventConsumer<%1$s>) new %2$s().createConsumer(bus, l, inv, %3$d, %1$s.class, %4$d, prov);
                tmp.add(c);
            }
            """.formatted(evtType, handlerSimple, prio, wrapped));

                w.write("                    }\n");
                w.write("                }\n");
            }
        }

        w.write("        }\n");
    }

    private String buildNonBuiltinFallbackBlock(final ExecutableElement m, final String owner, final String evtType) {
        final int paramCount = m.getParameters().size();
        final int wrapped = 0;
        final int nonWrapped = Math.max(0, paramCount - 1);
        return "{\n" +
                "    final bot.staro.rokit.ArgProvider<" + evtType + ">[] prov = new bot.staro.rokit.ArgProvider[0];\n" +
                """
                        final bot.staro.rokit.Invoker<%1$s> inv = new bot.staro.rokit.Invoker<%1$s>() {
                            @Override
                            public void call(final Object listener, final %1$s e, final Object[] wrapped, final Object[] provided) {
                                final %2$s l0 = (%2$s) listener;
                        """.formatted(evtType, owner) +
                "                        l0." + m.getSimpleName() + "(e);\n" +
                "                    }\n" +
                "                };\n" +
                "                @SuppressWarnings(\"unchecked\")\n" +
                "                final EventConsumer<" + evtType + "> c = (EventConsumer<" + evtType + ">) new " + extractHandler((TypeElement) m.getAnnotationMirrors().getFirst().getAnnotationType().asElement()).replaceFirst(".+\\.", "") + "().createConsumer(bus, l, inv, 0, " + evtType + ".class, " + wrapped + ", prov);\n" +
                "                tmp.add(c);\n" +
                "            }\n";
    }

    private String extractHandler(final TypeElement annotation) {
        final TypeElement marker = elements.getTypeElement("bot.staro.rokit.ListenerAnnotation");
        for (final AnnotationMirror am : annotation.getAnnotationMirrors()) {
            if (am.getAnnotationType().asElement().equals(marker)) {
                for (final var ev : am.getElementValues().entrySet()) {
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

    private static String paramClassList(final ExecutableElement element) {
        final StringBuilder builder = new StringBuilder();
        for (final VariableElement p : element.getParameters()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }

            builder.append(raw(p.asType().toString())).append(".class");
        }

        return builder.toString();
    }

    private static String raw(final String fqn) {
        final int idx = fqn.indexOf('<');
        return idx < 0 ? fqn : fqn.substring(0, idx);
    }

    private static Integer extractIntAttr(final AnnotationMirror am, final String name) {
        for (final var ev : am.getElementValues().entrySet()) {
            if (name.contentEquals(ev.getKey().getSimpleName())) {
                return Integer.parseInt(ev.getValue().getValue().toString());
            }
        }

        return null;
    }

    private static List<String> extractClassArrayAttr(final AnnotationMirror am, final String name) {
        final List<String> r = new ArrayList<>();
        for (final var ev : am.getElementValues().entrySet()) {
            if (name.contentEquals(ev.getKey().getSimpleName())) {
                final String s = ev.getValue().getValue().toString();
                if (s.startsWith("[") && s.endsWith("]")) {
                    final String body = s.substring(1, s.length() - 1).trim();
                    if (!body.isEmpty()) {
                        for (final String e : body.split(",")) {
                            r.add(e.trim());
                        }
                    }
                } else if (!s.isEmpty()) {
                    r.add(s);
                }
            }
        }

        return r;
    }

    private AnnotationMirror findListenerAnnotationMirror(final TypeElement annotationType) {
        final TypeElement marker = elements.getTypeElement("bot.staro.rokit.ListenerAnnotation");
        for (final AnnotationMirror am : annotationType.getAnnotationMirrors()) {
            if (am.getAnnotationType().asElement().equals(marker)) {
                return am;
            }
        }

        return null;
    }


    private record MethodInfo(TypeElement annotation, ExecutableElement method) {}

}
