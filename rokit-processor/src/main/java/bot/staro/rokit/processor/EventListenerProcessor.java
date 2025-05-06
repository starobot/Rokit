package bot.staro.rokit.processor;

import com.google.auto.service.AutoService;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

@SuppressWarnings("unused")
@AutoService(javax.annotation.processing.Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@SupportedAnnotationTypes("*")
public class EventListenerProcessor extends AbstractProcessor {
    private Elements elementUtils;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        elementUtils = env.getElementUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<TypeElement> listenerAnnos = new LinkedHashSet<>();
        TypeElement builtin = elementUtils.getTypeElement("bot.staro.rokit.Listener");
        if (builtin != null) {
            listenerAnnos.add(builtin);
        }

        TypeElement marker = elementUtils.getTypeElement("bot.staro.rokit.ListenerAnnotation");
        if (marker != null) {
            for (Element e : roundEnv.getElementsAnnotatedWith(marker)) {
                if (e.getKind() == ElementKind.ANNOTATION_TYPE) {
                    listenerAnnos.add((TypeElement) e);
                }
            }
        }

        boolean last = roundEnv.processingOver();
        if (listenerAnnos.isEmpty() && !last) {
            return false;
        }

        Map<String, List<MethodInfo>> byClass = new LinkedHashMap<>();
        for (TypeElement anno : listenerAnnos) {
            for (Element e : roundEnv.getElementsAnnotatedWith(anno)) {
                if (e instanceof ExecutableElement method) {
                    TypeElement cls = (TypeElement) method.getEnclosingElement();
                    byClass.computeIfAbsent(cls.getQualifiedName().toString(), k -> new ArrayList<>())
                            .add(new MethodInfo(anno, method));
                }
            }
        }

        if (byClass.isEmpty() && !last) {
            return false;
        }

        try {
            writeRegistry(builtin, listenerAnnos, byClass);
        } catch (IOException ex) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to write EventListenerRegistry: " + ex);
        }

        return true;
    }

    private void writeRegistry(TypeElement builtin, Set<TypeElement> listenerAnnos, Map<String, List<MethodInfo>> byClass) throws IOException {
        String pkg = "bot.staro.rokit.generated";
        String className = "EventListenerRegistry";
        JavaFileObject jfo;
        try {
            jfo = processingEnv.getFiler().createSourceFile(pkg + "." + className);
        } catch (javax.annotation.processing.FilerException ignored) {
            return;
        }

        Set<String> handlerFqns = new TreeSet<>();
        handlerFqns.add("bot.staro.rokit.impl.DefaultListenerHandler");
        for (TypeElement anno : listenerAnnos) {
            if (!anno.equals(builtin)) {
                handlerFqns.add(extractHandler(anno));
            }
        }

        try (Writer w = jfo.openWriter()) {
            w.write("package " + pkg + ";\n\n");
            w.write("import bot.staro.rokit.EventRegistry;\n");
            w.write("import bot.staro.rokit.EventConsumer;\n");
            w.write("import bot.staro.rokit.EventWrapper;\n");
            w.write("import java.util.Map;\n");
            w.write("import java.util.List;\n");
            w.write("import java.util.ArrayList;\n");
            w.write("import java.util.concurrent.ConcurrentHashMap;\n");
            w.write("import java.lang.reflect.Method;\n\n");
            for (String fqn : handlerFqns) {
                w.write("import " + fqn + ";\n");
            }

            w.write("\n");
            w.write("public final class " + className + " {\n");
            w.write("public static final Map<Object,List<EventConsumer<?>>> SUBSCRIBERS = new ConcurrentHashMap<>();\n\n");
            if (byClass.isEmpty()) {
                w.write("public static void register(EventRegistry bus, Object subscriber) {}\n\n");
                w.write("public static void unregister(EventRegistry bus, Object subscriber) {}\n\n");
            } else {
                w.write("public static void register(EventRegistry bus, Object subscriber) {\n");
                for (var entry : byClass.entrySet()) {
                    String sub = entry.getKey();
                    String pkgStr = elementUtils
                            .getPackageOf(elementUtils.getTypeElement(sub))
                            .getQualifiedName().toString();
                    String accessor = elementUtils
                            .getTypeElement(sub)
                            .getSimpleName() + "EventAccessor";
                    w.write("if (subscriber instanceof " + sub + ") {\n");
                    w.write(sub + " listener = (" + sub + ") subscriber;\n");
                    w.write("List<EventConsumer<?>> list = new ArrayList<>();\n");
                    for (MethodInfo mi : entry.getValue()) {
                        ExecutableElement m = mi.method();
                        TypeElement anno = mi.annotation();
                        List<? extends VariableElement> params = m.getParameters();
                        String evtType = rawType(params.getFirst().asType().toString());
                        String name = m.getSimpleName().toString();
                        int prio = extractPriority(m, anno);
                        boolean priv = m.getModifiers().contains(Modifier.PRIVATE);
                        if (anno.equals(builtin)) {
                            if (params.size() == 1) {
                                w.write("{\n");
                                w.write("EventConsumer<" + evtType + "> c = new EventConsumer<>() {\n");
                                w.write("@Override public void accept(" + evtType + " e) {\n");
                                if (priv) {
                                    w.write(pkgStr + "." + accessor + "." + name + "Accessor(listener, e);\n");
                                } else {
                                    w.write("listener." + name + "(e);\n");
                                }

                            } else {
                                int extraCount = params.size() - 1;
                                w.write("{\n");
                                w.write("EventConsumer<" + evtType + "> c = new EventConsumer<>() {\n");
                                w.write("private final EventWrapper<" + evtType + "> wrapper = bus.getWrapper(" + evtType + ".class);\n");
                                w.write("@Override public void accept(" + evtType + " e) {\n");
                                w.write("Object[] extras = wrapper.wrap(e);\n");
                                w.write("if (extras.length != " + extraCount + ") return;\n");
                                for (int i = 1; i < params.size(); i++) {
                                    String pt = rawType(params.get(i).asType().toString());
                                    w.write("if (!(extras[" + (i-1) + "] instanceof " + pt + ")) return;\n");
                                }

                                w.write("listener." + name + "(e");
                                for (int i = 1; i < params.size(); i++) {
                                    String pt = rawType(params.get(i).asType().toString());
                                    w.write(", (" + pt + ") extras[" + (i-1) + "]");
                                }

                                w.write(");\n");
                                w.write("listener." + name + "(e");
                                for (int i = 1; i < params.size(); i++) {
                                    String pt = rawType(params.get(i).asType().toString());
                                    w.write(", (" + pt + ") extras[" + (i - 1) + "]");
                                }

                                w.write(");\n");
                            }

                            w.write("}\n");
                            w.write("@Override public Object getInstance() { return listener; }\n");
                            w.write("@Override public int getPriority() { return " + prio + "; }\n");
                            w.write("@Override public Class<" + evtType + "> getEventType() { return " + evtType + ".class; }\n");
                            w.write("};\n");
                            w.write("list.add(c);\n");
                            w.write("bus.internalRegister(" + evtType + ".class, c);\n");
                            w.write("}\n");
                        } else {
                            String handler = extractHandler(anno).replaceFirst(".+\\.", "");
                            w.write("{\n");
                            w.write("Method method = getMethod(listener, \"" + name + "\", " + params.size() + ");\n");
                            w.write("var consumer = new " + handler +
                                    "().createConsumer(bus, listener, method, " + prio + ", " + evtType + ".class);\n");
                            w.write("list.add(consumer);\n");
                            w.write("}\n");
                        }
                    }

                    w.write("SUBSCRIBERS.put(listener, list);\n");
                    w.write("}\n");
                }

                w.write("}\n\n");
                w.write("public static void unregister(EventRegistry bus, Object subscriber) {\n");
                w.write("List<EventConsumer<?>> list = SUBSCRIBERS.remove(subscriber);\n");
                w.write("if (list != null) {\n");
                w.write("for (EventConsumer<?> c : list) {\n");
                w.write("bus.internalUnregister(c.getEventType(), c);\n");
                w.write("}\n");
                w.write("}\n");
                w.write("}\n\n");
            }

            w.write("private static Method getMethod(Object subscriber, String methodName, int paramCount) {\n");
            w.write("Class<?> cls = subscriber.getClass();\n");
            w.write("while (cls != null) {\n");
            w.write("for (Method m : cls.getDeclaredMethods()) {\n");
            w.write("if (m.getName().equals(methodName) && m.getParameterCount() == paramCount) {\n");
            w.write("m.setAccessible(true);\n");
            w.write("return m;\n");
            w.write("}\n");
            w.write("}\n");
            w.write("cls = cls.getSuperclass();\n");
            w.write("}\n");
            w.write("throw new RuntimeException(\"Listener method not found: \" + methodName);\n");
            w.write("}\n\n");

            Set<String> eventTypes = new TreeSet<>();
            for (List<MethodInfo> methods : byClass.values()) {
                for (MethodInfo mi : methods) {
                    String raw = rawType(mi.method().getParameters().getFirst().asType().toString());
                    eventTypes.add(raw + ".class");
                }
            }

            w.write("public static final Class<?>[] EVENT_TYPES = new Class<?>[]{\n");
            for (String et : eventTypes) {
                w.write(et + ",\n");
            }

            w.write("};\n\n");
            w.write("public static int getEventId(Class<?> cls) {\n");
            int idx = 0;
            for (String et : eventTypes) {
                String cn = et.substring(0, et.length() - ".class".length());
                w.write("if (cls == " + cn + ".class) return " + (idx++) + ";\n");
            }

            w.write("return -1;\n");
            w.write("}\n\n");
            w.write("}\n");
        }
    }


    private String extractHandler(TypeElement anno) {
        TypeElement marker = elementUtils.getTypeElement("bot.staro.rokit.ListenerAnnotation");
        for (AnnotationMirror am : anno.getAnnotationMirrors()) {
            if (am.getAnnotationType().asElement().equals(marker)) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> ev : am.getElementValues().entrySet()) {
                    if ("handler".equals(ev.getKey().getSimpleName().toString())) {
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
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> ev : am.getElementValues().entrySet()) {
                    if ("priority".equals(ev.getKey().getSimpleName().toString())) {
                        return Integer.parseInt(ev.getValue().getValue().toString());
                    }
                }
            }
        }

        return 0;
    }

    private String rawType(String full) {
        int i = full.indexOf('<');
        return i < 0 ? full : full.substring(0, i);
    }

    private record MethodInfo(TypeElement annotation, ExecutableElement method) {}

}
