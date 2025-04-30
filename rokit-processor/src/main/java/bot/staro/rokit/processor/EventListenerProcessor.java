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
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        Set<TypeElement> listenerAnnos = new LinkedHashSet<>();
        TypeElement builtin = elementUtils.getTypeElement("bot.staro.rokit.Listener");
        if (builtin != null) {
            listenerAnnos.add(builtin);
        }

        TypeElement marker = elementUtils.getTypeElement("bot.staro.rokit.ListenerAnnotation");
        if (marker != null) {
            for (Element e : roundEnv.getElementsAnnotatedWith(marker)) {
                if (e.getKind() == ElementKind.ANNOTATION_TYPE) {
                    listenerAnnos.add((TypeElement)e);
                }
            }
        }

        if (listenerAnnos.isEmpty()) {
            return false;
        }

        Map<String,List<MethodInfo>> byClass = new LinkedHashMap<>();
        for (TypeElement anno : listenerAnnos) {
            for (Element e : roundEnv.getElementsAnnotatedWith(anno)) {
                if (e instanceof ExecutableElement method) {
                    TypeElement cls = (TypeElement)method.getEnclosingElement();
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

    private void writeRegistry(TypeElement builtin, Set<TypeElement> listenerAnnos, Map<String,List<MethodInfo>> byClass) throws IOException {
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
            boolean hasPrivateMethods = false;
            Set<String> privateMethodNames = new HashSet<>();

            for (MethodInfo mi : entry.getValue()) {
                ExecutableElement m = mi.method();
                if (m.getModifiers().contains(Modifier.PRIVATE)) {
                    hasPrivateMethods = true;
                    privateMethodNames.add(m.getSimpleName().toString());
                }
            }

            if (hasPrivateMethods) {
                String accessorClassName = classElement.getSimpleName() + "EventAccessor";
                String accessorFqcn = classPackage + "." + accessorClassName;
                JavaFileObject accessorFile = processingEnv.getFiler().createSourceFile(accessorFqcn);
                try (Writer writer = accessorFile.openWriter()) {
                    writer.write("package " + classPackage + ";\n\n");
                    writer.write("// Generated accessor for private event listener methods\n");
                    writer.write("class " + accessorClassName + " {\n");
                    for (String methodName : privateMethodNames) {
                        for (MethodInfo mi : entry.getValue()) {
                            ExecutableElement m = mi.method();
                            if (m.getSimpleName().toString().equals(methodName) && m.getModifiers().contains(Modifier.PRIVATE)) {
                                List<? extends VariableElement> params = m.getParameters();
                                String eventType = rawType(params.getFirst().asType().toString());

                                writer.write("    static void " + methodName + "Accessor(" +
                                        classElement.getSimpleName() + " instance, " +
                                        eventType + " event) {\n");
                                writer.write("        instance." + methodName + "(event);\n");
                                writer.write("    }\n\n");
                            }
                        }
                    }

                    writer.write("}\n");
                }
            }
        }

        try (Writer w = jfo.openWriter()) {
            w.write("package " + pkg + "; \n\n");
            w.write("import bot.staro.rokit.EventRegistry; \n");
            w.write("import bot.staro.rokit.EventConsumer; \n");
            w.write("import bot.staro.rokit.EventWrapper; \n");
            w.write("import java.util.*; \n");
            w.write("import java.lang.reflect.Method; \n\n");
            for (String fqcn : handlerFqns) {
                w.write("import " + fqcn + "; \n");
            }

            w.write("\n");
            w.write("public final class " + className + " { \n\n");
            w.write("    public static final Map<Object,List<EventConsumer<?>>> SUBSCRIBERS = new HashMap<>(); \n\n");
            w.write("    public static void register(EventRegistry bus, Object subscriber) { \n");
            for (var entry : byClass.entrySet()) {
                String subType = entry.getKey();
                TypeElement classElement = elementUtils.getTypeElement(subType);
                String classPackage = elementUtils.getPackageOf(classElement).getQualifiedName().toString();
                String accessorClassName = classElement.getSimpleName() + "EventAccessor";
                w.write("        if (subscriber instanceof " + subType + ") { \n");
                w.write("            " + subType + " listener = (" + subType + ")subscriber; \n");
                w.write("            List<EventConsumer<?>> list = new ArrayList<>(); \n");
                for (MethodInfo mi : entry.getValue()) {
                    ExecutableElement m = mi.method();
                    TypeElement anno = mi.annotation();
                    List<? extends VariableElement> params = m.getParameters();
                    String eventType = rawType(params.getFirst().asType().toString());
                    String methodName = m.getSimpleName().toString();
                    int prio = extractPriority(m, anno);
                    boolean isPrivate = m.getModifiers().contains(Modifier.PRIVATE);
                    if (anno.equals(builtin) && params.size() == 1) {
                        w.write("            { \n");
                        w.write("                EventConsumer<" + eventType + "> c = new EventConsumer<>() {\n");
                        w.write("                    @Override\n");
                        w.write("                    public void accept(" + eventType + " e) {\n");
                        if (isPrivate) {
                            w.write("                        " + classPackage + "." + accessorClassName + "." + methodName + "Accessor(listener, e);\n");
                        } else {
                            w.write("                        listener." + methodName + "(e);\n");
                        }

                        w.write("                    }\n\n");
                        w.write("                    @Override public Object getInstance() { return listener; }\n");
                        w.write("                    @Override public int getPriority()    { return " + prio + "; }\n");
                        w.write("                    @Override public Class<" + eventType + "> getEventType() { return " + eventType + ".class; }\n");
                        w.write("                };\n");
                        w.write("                list.add(c);\n");
                        w.write("                bus.internalRegister(" + eventType + ".class, c);\n");
                        w.write("            } \n");
                    } else {
                        String handlerSimple = extractHandler(anno).replaceFirst(".+\\.", "");
                        w.write("            { \n");
                        w.write("                Method m = "
                                + subType
                                + ".class.getDeclaredMethod(\""
                                + methodName
                                + "\", "
                                + eventType
                                + ".class); \n");
                        w.write("                m.setAccessible(true); \n");
                        w.write("                var consumer = new "
                                + handlerSimple
                                + "()\n");
                        w.write("                    .createConsumer(\n");
                        w.write("                        bus,\n");
                        w.write("                        listener,\n");
                        w.write("                        m,\n");
                        w.write("                        "
                                + prio
                                + ",\n");
                        w.write("                        "
                                + eventType
                                + ".class\n");
                        w.write("                    ); \n");
                        w.write("                list.add(consumer); \n");
                        w.write("            } \n");
                    }
                }

                w.write("            SUBSCRIBERS.put(listener, list); \n");
                w.write("        } \n");
            }

            w.write("    } \n\n");
            w.write("    public static void unregister(EventRegistry bus, Object subscriber) { \n");
            w.write("        List<EventConsumer<?>> list = SUBSCRIBERS.remove(subscriber); \n");
            w.write("        if (list != null) { \n");
            w.write("            for (EventConsumer<?> c : list) { \n");
            w.write("                bus.internalUnregister(c.getEventType(), c); \n");
            w.write("            } \n");
            w.write("        } \n");
            w.write("    } \n\n");
            w.write("    private static Method getMethod(\n");
            w.write("        Object subscriber,\n");
            w.write("        String methodName,\n");
            w.write("        int paramCount\n");
            w.write("    ) {\n");
            w.write("        for (Method m : subscriber.getClass().getDeclaredMethods()) {\n");
            w.write("            if (m.getName().equals(methodName)\n");
            w.write("                && m.getParameterCount() == paramCount) {\n");
            w.write("                m.setAccessible(true);\n");
            w.write("                return m;\n");
            w.write("            }\n");
            w.write("        }\n");
            w.write("        throw new RuntimeException(\"Listener method not found: \" + methodName);\n");
            w.write("    }\n\n");

            w.write("}\n");
        }
    }

    private String extractHandler(TypeElement anno) {
        TypeElement marker = elementUtils.getTypeElement("bot.staro.rokit.ListenerAnnotation");
        for (AnnotationMirror am : anno.getAnnotationMirrors()) {
            if (am.getAnnotationType().asElement().equals(marker)) {
                for (var ev : am.getElementValues().entrySet()) {
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
                for (var ev : am.getElementValues().entrySet()) {
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