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

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        elements = env.getElementUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        Set<TypeElement> listenerAnnos = new LinkedHashSet<>();
        TypeElement builtin = elements.getTypeElement("bot.staro.rokit.Listener");
        if (builtin != null) {
            listenerAnnos.add(builtin);
        }

        TypeElement marker = elements.getTypeElement("bot.staro.rokit.ListenerAnnotation");
        if (marker != null) {
            for (Element e : env.getElementsAnnotatedWith(marker)) {
                if (e.getKind() == ElementKind.ANNOTATION_TYPE) {
                    listenerAnnos.add((TypeElement) e);
                }
            }
        }

        Map<String, List<MethodInfo>> byClass = new LinkedHashMap<>();
        for (TypeElement anno : listenerAnnos) {
            for (Element e : env.getElementsAnnotatedWith(anno)) {
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

    private void writeFiles(TypeElement builtin,
                            Set<TypeElement> listenerAnnos,
                            Map<String, List<MethodInfo>> byClass) throws IOException {

        String pkg = processingEnv.getOptions().getOrDefault("rokit.generatedPackage", DEF_PKG);
        try (Writer w = processingEnv.getFiler()
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

        Set<String> handlers = new TreeSet<>();
        handlers.add("bot.staro.rokit.impl.DefaultListenerHandler");
        for (TypeElement anno : listenerAnnos) {
            if (!anno.equals(builtin)) {
                handlers.add(extractHandler(anno));
            }
        }

        try (Writer w = jfo.openWriter()) {
            w.write("package " + pkg + ";\n\n");
            w.write("import bot.staro.rokit.*;\n");
            w.write("import bot.staro.rokit.gen.GeneratedRegistry;\n");
            w.write("import java.util.*;\n");
            w.write("import java.util.concurrent.ConcurrentHashMap;\n");
            w.write("import java.lang.reflect.Method;\n");
            for (String h : handlers) {
                w.write("import " + h + ";\n");
            }

            w.write("\npublic final class " + GEN_NAME + " implements GeneratedRegistry {\n");
            w.write("    public static final Map<Object, List<EventConsumer<?>>> SUBSCRIBERS = new ConcurrentHashMap<>();\n\n");
            w.write("    @Override\n");
            w.write("    public void register(ListenerRegistry bus, Object sub) {\n");
            for (var entry : byClass.entrySet()) {
                String owner = entry.getKey();
                String simple = elements.getTypeElement(owner).getSimpleName().toString();
                String accessor = simple + "EventAccessor";
                String ownerPkg = elements.getPackageOf(elements.getTypeElement(owner))
                        .getQualifiedName().toString();

                w.write("        if (sub instanceof " + owner + " l) {\n");
                w.write("            List<EventConsumer<?>> list = new ArrayList<>();\n");

                for (MethodInfo mi : entry.getValue()) {
                    ExecutableElement m = mi.method();
                    String evtType = raw(m.getParameters().getFirst().asType().toString());
                    String name = m.getSimpleName().toString();
                    int prio = extractPriority(m, mi.annotation());
                    boolean priv = m.getModifiers().contains(Modifier.PRIVATE);

                    if (mi.annotation().equals(builtin)) {
                        if (m.getParameters().size() == 1) {
                            w.write("            {\n");
                            w.write("                EventConsumer<" + evtType + "> c = new EventConsumer<>() {\n");
                            w.write("                    @Override public void accept(" + evtType + " e) {\n");
                            if (priv) {
                                w.write("                        " + ownerPkg + "." + accessor + "." + name + "Accessor(l, e);\n");
                            } else {
                                w.write("                        l." + name + "(e);\n");
                            }
                        } else {
                            int extras = m.getParameters().size() - 1;
                            w.write("            {\n");
                            w.write("                EventConsumer<" + evtType + "> c = new EventConsumer<>() {\n");
                            w.write("                    final EventWrapper<" + evtType + "> w = bus.getWrapper(" + evtType + ".class);\n");
                            w.write("                    @Override public void accept(" + evtType + " e) {\n");
                            w.write("                        Object[] x = w.wrap(e);\n");
                            w.write("                        if (x.length != " + extras + ") { return; }\n");
                            for (int i = 1; i < m.getParameters().size(); i++) {
                                String pt = raw(m.getParameters().get(i).asType().toString());
                                w.write("                        if (!(x[" + (i - 1) + "] instanceof " + pt + ")) { return; }\n");
                            }

                            w.write("                        l." + name + "(e");
                            for (int i = 1; i < m.getParameters().size(); i++) {
                                String pt = raw(m.getParameters().get(i).asType().toString());
                                w.write(", (" + pt + ") x[" + (i - 1) + "]");
                            }

                            w.write(");\n");
                        }

                        w.write("                    }\n");
                        w.write("                    @Override public Object getInstance() { return l; }\n");
                        w.write("                    @Override public int getPriority() { return " + prio + "; }\n");
                        w.write("                    @Override public Class<" + evtType + "> getEventType() { return " + evtType + ".class; }\n");
                        w.write("                };\n");
                        w.write("                list.add(c);\n");
                        w.write("                bus.internalRegister(" + evtType + ".class, c);\n");
                        w.write("            }\n");
                    } else {
                        String h = extractHandler(mi.annotation()).replaceFirst(".+\\.", "");
                        w.write("            {\n");
                        w.write("                Method mtd = getMethod(l, \"" + name + "\", " + m.getParameters().size() + ");\n");
                        w.write("                var c = new " + h + "().createConsumer(bus, l, mtd, " + prio + ", " + evtType + ".class);\n");
                        w.write("                list.add(c);\n");
                        w.write("            }\n");
                    }
                }

                w.write("            SUBSCRIBERS.put(l, list);\n");
                w.write("        }\n");
            }

            w.write("    }\n\n");

            w.write("    @Override\n");
            w.write("    public void unregister(ListenerRegistry bus, Object sub) {\n");
            w.write("        List<EventConsumer<?>> list = SUBSCRIBERS.remove(sub);\n");
            w.write("        if (list != null) {\n");
            w.write("            for (EventConsumer<?> c : list) {\n");
            w.write("                bus.internalUnregister(c.getEventType(), c);\n");
            w.write("            }\n");
            w.write("        }\n");
            w.write("    }\n\n");

            w.write("    private static Method getMethod(Object o, String n, int p) {\n");
            w.write("        Class<?> c = o.getClass();\n");
            w.write("        while (c != null) {\n");
            w.write("            for (Method m : c.getDeclaredMethods()) {\n");
            w.write("                if (m.getName().equals(n) && m.getParameterCount() == p) {\n");
            w.write("                    m.setAccessible(true);\n");
            w.write("                    return m;\n");
            w.write("                }\n");
            w.write("            }\n");
            w.write("            c = c.getSuperclass();\n");
            w.write("        }\n");
            w.write("        throw new RuntimeException(n);\n");
            w.write("    }\n\n");
            Set<String> types = new TreeSet<>();
            for (List<MethodInfo> methods : byClass.values()) {
                for (MethodInfo mi : methods) {
                    types.add(raw(mi.method().getParameters().getFirst().asType().toString()) + ".class");
                }
            }

            w.write("    public static final Class<?>[] EVENT_TYPES = {\n");
            for (String t : types) {
                w.write("        " + t + ",\n");
            }

            w.write("    };\n\n");

            w.write("    @Override\n");
            w.write("    public int getEventId(Class<?> c) {\n");
            int id = 0;
            for (String t : types) {
                String cls = t.substring(0, t.length() - 6);
                w.write("        if (c == " + cls + ".class) { return " + id + "; }\n");
                id++;
            }

            w.write("        return -1;\n");
            w.write("    }\n\n");

            w.write("    @Override public Class<?>[] eventTypes() { return EVENT_TYPES; }\n");
            w.write("    @Override public java.util.Map<Object, java.util.List<EventConsumer<?>>> subscribers() { return SUBSCRIBERS; }\n");
            w.write("}\n");
        }
    }

    private String extractHandler(TypeElement anno) {
        TypeElement marker = elements.getTypeElement("bot.staro.rokit.ListenerAnnotation");
        for (AnnotationMirror am : anno.getAnnotationMirrors()) {
            if (am.getAnnotationType().asElement().equals(marker)) {
                for (var ev : am.getElementValues().entrySet()) {
                    if ("handler".contentEquals(ev.getKey().getSimpleName())) {
                        return ev.getValue().getValue().toString();
                    }
                }
            }
        }

        return "bot.staro.rokit.impl.DefaultListenerHandler";
    }

    private int extractPriority(ExecutableElement m, TypeElement anno) {
        for (AnnotationMirror am : m.getAnnotationMirrors()) {
            if (am.getAnnotationType().asElement().equals(anno)) {
                for (var ev : am.getElementValues().entrySet()) {
                    if ("priority".contentEquals(ev.getKey().getSimpleName())) {
                        return Integer.parseInt(ev.getValue().getValue().toString());
                    }
                }
            }
        }

        return 0;
    }

    private static String raw(String fqn) {
        int idx = fqn.indexOf('<');
        return idx < 0 ? fqn : fqn.substring(0, idx);
    }

    private record MethodInfo(TypeElement annotation, ExecutableElement method) {}

}
