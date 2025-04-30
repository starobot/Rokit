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

        if (listenerAnnos.isEmpty()) {
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

        if (byClass.isEmpty()) {
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
        JavaFileObject jfo = processingEnv.getFiler().createSourceFile(pkg + "." + className);
        Set<String> handlerFqns = new TreeSet<>();
        handlerFqns.add("bot.staro.rokit.impl.DefaultListenerHandler");
        for (TypeElement anno : listenerAnnos) {
            if (!anno.equals(builtin)) {
                handlerFqns.add(extractHandler(anno));
            }
        }

        for (var entry : byClass.entrySet()) {
            String classFqn = entry.getKey();
            TypeElement classElement = elementUtils.getTypeElement(classFqn);
            String classPackage = elementUtils.getPackageOf(classElement).getQualifiedName().toString();
            boolean hasPrivate = false;
            Set<String> privateNames = new HashSet<>();
            for (MethodInfo mi : entry.getValue()) {
                if (mi.method().getModifiers().contains(Modifier.PRIVATE)) {
                    hasPrivate = true;
                    privateNames.add(mi.method().getSimpleName().toString());
                }
            }

            if (hasPrivate) {
                String accessorName = classElement.getSimpleName() + "EventAccessor";
                String accessorFqcn = classPackage + "." + accessorName;
                JavaFileObject accFile = processingEnv.getFiler().createSourceFile(accessorFqcn);
                try (Writer w = accFile.openWriter()) {
                    w.write("package " + classPackage + ";\n\n");
                    w.write("class " + accessorName + " {\n");
                    for (String name : privateNames) {
                        for (MethodInfo mi : entry.getValue()) {
                            ExecutableElement m = mi.method();
                            if (m.getSimpleName().toString().equals(name) && m.getModifiers().contains(Modifier.PRIVATE)) {
                                String evt = rawType(m.getParameters().getFirst().asType().toString());
                                w.write("    static void " + name + "Accessor(" + classElement.getSimpleName() + " instance, " + evt + " event) {\n");
                                w.write("        instance." + name + "(event);\n");
                                w.write("    }\n");
                            }
                        }
                    }

                    w.write("}\n");
                }
            }
        }

        try (Writer w = jfo.openWriter()) {
            w.write("package " + pkg + ";\n\n");
            w.write("import bot.staro.rokit.EventRegistry;\n");
            w.write("import bot.staro.rokit.EventConsumer;\n");
            w.write("import bot.staro.rokit.EventWrapper;\n");
            w.write("import java.util.*;\n");
            w.write("import java.lang.reflect.Method;\n\n");
            for (String fqn : handlerFqns) {
                w.write("import " + fqn + ";\n");
            }

            w.write("public final class " + className + " {\n");
            w.write("    public static final Map<Object,List<EventConsumer<?>>> SUBSCRIBERS = new HashMap<>();\n\n");
            w.write("    public static void register(EventRegistry bus, Object subscriber) {\n");
            for (var entry : byClass.entrySet()) {
                String sub = entry.getKey();
                TypeElement clsElem = elementUtils.getTypeElement(sub);
                String pkgStr = elementUtils.getPackageOf(clsElem).getQualifiedName().toString();
                String accessor = clsElem.getSimpleName() + "EventAccessor";
                w.write("        if (subscriber instanceof " + sub + ") {\n");
                w.write("            " + sub + " listener = (" + sub + ")subscriber;\n");
                w.write("            List<EventConsumer<?>> list = new ArrayList<>();\n");
                for (MethodInfo mi : entry.getValue()) {
                    ExecutableElement m = mi.method();
                    TypeElement anno = mi.annotation();
                    List<? extends VariableElement> params = m.getParameters();
                    String evtType = rawType(params.getFirst().asType().toString());
                    String name = m.getSimpleName().toString();
                    int prio = extractPriority(m, anno);
                    boolean priv = m.getModifiers().contains(Modifier.PRIVATE);
                    if (anno.equals(builtin) && params.size() == 1) {
                        w.write("            {\n");
                        w.write("                EventConsumer<" + evtType + "> c = new EventConsumer<>() {\n");
                        w.write("                    @Override public void accept(" + evtType + " e) {\n");
                        if (priv) {
                            w.write("                        " + pkgStr + "." + accessor + "." + name + "Accessor(listener, e);\n");
                        } else {
                            w.write("                        listener." + name + "(e);\n");
                        }
                        w.write("                    }\n");
                        w.write("                    @Override public Object getInstance() { return listener; }\n");
                        w.write("                    @Override public int getPriority() { return " + prio + "; }\n");
                        w.write("                    @Override public Class<" + evtType + "> getEventType() { return " + evtType + ".class; }\n");
                        w.write("                };\n");
                        w.write("                list.add(c);\n");
                        w.write("                bus.internalRegister(" + evtType + ".class, c);\n");
                        w.write("            }\n");
                    } else {
                        String handler = extractHandler(anno).replaceFirst(".+\\.", "");
                        w.write("            {\n");
                        w.write("                Method method = getMethod(\n");
                        w.write("                    listener,\n");
                        w.write("                    \"" + name + "\",\n");
                        w.write("                    " + params.size() + "\n");
                        w.write("                );\n");
                        w.write("                var consumer = new " + handler + "()" + ".createConsumer(\n");
                        w.write("                    bus,\n");
                        w.write("                    listener,\n");
                        w.write("                    method,\n");
                        w.write("                    " + prio + ",\n");
                        w.write("                    " + evtType + ".class\n");
                        w.write("                );\n");
                        w.write("                list.add(consumer);\n");
                        w.write("            }\n");
                    }
                }
                w.write("            SUBSCRIBERS.put(listener, list);\n");
                w.write("        }\n");
            }

            w.write("    }\n\n");
            w.write("    public static void unregister(EventRegistry bus, Object subscriber) {\n");
            w.write("        List<EventConsumer<?>> list = SUBSCRIBERS.remove(subscriber);\n");
            w.write("        if (list != null) {\n");
            w.write("            for (EventConsumer<?> c : list) {\n");
            w.write("                bus.internalUnregister(c.getEventType(), c);\n");
            w.write("            }\n");
            w.write("        }\n");
            w.write("    }\n\n");
            w.write("    private static Method getMethod(Object subscriber, String methodName, int paramCount) {\n");
            w.write("        Class<?> cls = subscriber.getClass();\n");
            w.write("        while (cls != null) {\n");
            w.write("            for (Method m : cls.getDeclaredMethods()) {\n");
            w.write("                if (m.getName().equals(methodName) && m.getParameterCount() == paramCount) {\n");
            w.write("                    m.setAccessible(true);\n");
            w.write("                    return m;\n");
            w.write("                }\n");
            w.write("            }\n");
            w.write("            cls = cls.getSuperclass();\n");
            w.write("        }\n");
            w.write("        throw new RuntimeException(\"Listener method not found: \" + methodName);\n");
            w.write("    }\n");
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
