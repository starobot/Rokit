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
            final String evtType = raw(m.getParameters().getFirst().asType().toString());
            final String name = m.getSimpleName().toString();
            final int prio = extractPriority(m, mi.annotation());
            final boolean priv = m.getModifiers().contains(Modifier.PRIVATE);
            final String decl = ((TypeElement) m.getEnclosingElement()).getQualifiedName().toString();
            if (mi.annotation().equals(builtin)) {
                if (m.getParameters().size() == 1) {
                    if (!priv) {
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
                        w.write("""
                            {
                                final Method mtd;
                                try {
                                    mtd = %3$s.class.getDeclaredMethod("%2$s", %1$s.class);
                                    mtd.setAccessible(true);
                                } catch (Throwable ex) { throw new RuntimeException(ex); }
                                final EventConsumer<%1$s> c = new EventConsumer<>() {
                                    @Override public void accept(%1$s e) {
                                        try { mtd.invoke(l, e); } catch (Throwable t) { throw new RuntimeException(t); }
                                    }
                                    @Override public int getPriority() { return %4$d; }
                                    @Override public Class<%1$s> getEventType() { return %1$s.class; }
                                };
                                tmp.add(c);
                            }
                            """.formatted(evtType, name, decl, prio));
                    }
                } else {
                    final int extras = m.getParameters().size() - 1;
                    if (!priv) {
                        w.write("""
                            {
                                final EventConsumer<%1$s> c = new EventConsumer<>() {
                                    final EventWrapper<%1$s> w0 = bus.getWrapper(%1$s.class);
                                    @Override public void accept(%1$s e) {
                                        final Object[] x = w0.wrap(e);
                                        if (x.length != %2$d) return;
                            """.formatted(evtType, extras));
                        for (int i = 1; i < m.getParameters().size(); i++) {
                            final String pt = raw(m.getParameters().get(i).asType().toString());
                            w.write("                                        if (!(x[%d] instanceof %s)) return;%n".formatted(i - 1, pt));
                        }

                        w.write("                                        l.%s(e".formatted(name));
                        for (int i = 1; i < m.getParameters().size(); i++) {
                            final String pt = raw(m.getParameters().get(i).asType().toString());
                            w.write(", (%s) x[%d]".formatted(pt, i - 1));
                        }

                        w.write(");\n");
                        w.write("""
                                    }
                                    @Override public int getPriority() { return %1$d; }
                                    @Override public Class<%2$s> getEventType() { return %2$s.class; }
                                };
                                tmp.add(c);
                            }
                            """.formatted(prio, evtType));
                    } else {
                        w.write("""
                            {
                                final Method mtd;
                                try {
                                    mtd = %4$s.class.getDeclaredMethod("%2$s", %3$s);
                                    mtd.setAccessible(true);
                                } catch (Throwable ex) { throw new RuntimeException(ex); }
                                final EventConsumer<%1$s> c = new EventConsumer<>() {
                                    final EventWrapper<%1$s> w0 = bus.getWrapper(%1$s.class);
                                    @Override public void accept(%1$s e) {
                                        final Object[] x = w0.wrap(e);
                                        if (x.length != %5$d) return;
                                        final Object[] args = new Object[%6$d];
                                        args[0] = e;
                            """.formatted(
                                evtType, name, paramClassList(m), decl, extras, m.getParameters().size()
                        ));
                        for (int i = 1; i < m.getParameters().size(); i++) {
                            w.write("                                        args[%d] = x[%d];%n".formatted(i, i - 1));
                        }

                        w.write("""
                                        try { mtd.invoke(l, args); } catch (Throwable t) { throw new RuntimeException(t); }
                                    }
                                    @Override public int getPriority() { return %1$d; }
                                    @Override public Class<%2$s> getEventType() { return %2$s.class; }
                                };
                                tmp.add(c);
                            }
                            """.formatted(prio, evtType));
                    }
                }
            } else {
                final String handlerSimple = extractHandler(mi.annotation()).replaceFirst(".+\\.", "");
                w.write("""
                    {
                        final Method mtd;
                        try {
                            mtd = %4$s.class.getDeclaredMethod("%1$s", %2$s);
                            mtd.setAccessible(true);
                        } catch (Throwable ex) { throw new RuntimeException(ex); }
                        final EventConsumer<?> c = new %3$s().createConsumer(bus, l, mtd, %5$d, %6$s.class);
                        tmp.add(c);
                    }
                    """.formatted(name, paramClassList(m), handlerSimple, decl, prio, evtType));
            }
        }

        w.write("        }\n");
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

    private record MethodInfo(TypeElement annotation, ExecutableElement method) {}

}
