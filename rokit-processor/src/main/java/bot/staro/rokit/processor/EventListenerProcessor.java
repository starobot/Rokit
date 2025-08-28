package bot.staro.rokit.processor;

import bot.staro.rokit.spi.ContextualParamBinder;
import bot.staro.rokit.spi.ParamBinder;
import bot.staro.rokit.spi.ProviderAwareBinder;
import com.google.auto.service.AutoService;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
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
@SupportedOptions({"rokit.generatedPackage"})
public final class EventListenerProcessor extends AbstractProcessor {
    private static final String GEN_PKG = "bot.staro.rokit.generated";
    private static final String BOOTSTRAP = "GeneratedBootstrap";
    private static final String[] PAYLOAD_ACCESSOR_CANDIDATES = new String[] {
            "getPacket", "getPayload", "getObject", "payload", "get", "value", "getValue", "object", "data", "setting", "screen"
    };

    private Elements elements;
    private Messager messager;
    private Filer filer;
    private String genPkg = GEN_PKG;
    private boolean hadError = false;

    private List<ParamBinder> paramBinders;

    @Override
    public synchronized void init(final ProcessingEnvironment environment) {
        super.init(environment);
        this.elements = environment.getElementUtils();
        this.messager = environment.getMessager();
        this.filer = environment.getFiler();

        final String opt = environment.getOptions().get("rokit.generatedPackage");
        if (opt != null && !opt.isBlank()) {
            this.genPkg = opt.trim();
        }

        this.paramBinders = new ArrayList<>();
        try {
            for (var b : ServiceLoader.load(ParamBinder.class, getClass().getClassLoader())) {
                this.paramBinders.add(b);
            }
        } catch (final Exception ex) {
            messager.printMessage(Diagnostic.Kind.WARNING, "Rokit ServiceLoader failed: " + ex);
        }

        final String extraBinders = environment.getOptions().get("rokit.extraBinders");
        if (extraBinders != null && !extraBinders.isBlank()) {
            for (final String binderClassName : extraBinders.split(",")) {
                final String trimmed = binderClassName.trim();
                if (trimmed.isEmpty()) continue;
                try {
                    final Class<?> binderClass = Class.forName(trimmed);
                    final ParamBinder binderInstance = (ParamBinder) binderClass.getConstructor().newInstance();
                    this.paramBinders.add(binderInstance);
                } catch (final Exception ex) {
                    error(null, "Failed to manually load binder '%s': %s", trimmed, ex);
                }
            }
        }
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        final TypeElement listenerAnno = elements.getTypeElement("bot.staro.rokit.Listener");
        final TypeElement listenerMarker = elements.getTypeElement("bot.staro.rokit.ListenerAnnotation");
        final Set<TypeElement> listenerAnnotations = new LinkedHashSet<>();
        if (listenerAnno != null) {
            listenerAnnotations.add(listenerAnno);
        }

        if (listenerMarker != null) {
            for (final Element e : roundEnv.getElementsAnnotatedWith(listenerMarker)) {
                if (e.getKind() == ElementKind.ANNOTATION_TYPE) {
                    listenerAnnotations.add((TypeElement) e);
                }
            }
        }

        if (listenerAnnotations.isEmpty()) {
            return false;
        }

        final Map<String, List<MethodInfo>> byOwner = new LinkedHashMap<>();
        for (final TypeElement anno : listenerAnnotations) {
            for (final Element e : roundEnv.getElementsAnnotatedWith(anno)) {
                if (!(e instanceof ExecutableElement method)) {
                    continue;
                }

                final String owner = ((TypeElement) method.getEnclosingElement()).getQualifiedName().toString();
                byOwner.computeIfAbsent(owner, k -> new ArrayList<>()).add(new MethodInfo(anno, method));
            }
        }

        if (byOwner.isEmpty()) {
            return false;
        }

        final Map<String, EventModel> events = new LinkedHashMap<>();
        for (final List<MethodInfo> methods : byOwner.values()) {
            for (final MethodInfo mi : methods) {
                final ExecutableElement method = mi.method;
                if (!method.getModifiers().contains(Modifier.PUBLIC)) {
                    continue;
                }

                final List<? extends VariableElement> params = method.getParameters();
                if (params.isEmpty()) {
                    error(method, "Listener method must have at least the event parameter.");
                    continue;
                }

                final VariableElement eventParam = params.getFirst();
                final String eventFqn = eventParam.asType().toString();

                final EventModel event = events.computeIfAbsent(eventFqn, EventModel::new);

                final String ownerFqn = ((TypeElement) method.getEnclosingElement()).getQualifiedName().toString();
                final String methodName = method.getSimpleName().toString();
                final int priority = extractPriority(method, mi.annotation);

                final TypeElement contextualListenerMeta = elements.getTypeElement("bot.staro.rokit.ContextualListener");
                final boolean isContextualListener = mi.annotation.getAnnotationMirrors()
                        .stream()
                        .anyMatch(mirror -> mirror.getAnnotationType().asElement().equals(contextualListenerMeta));

                final List<ParamPlan> paramPlans = new ArrayList<>();
                final List<String> signatureTypes = new ArrayList<>();

                for (int i = 1; i < params.size(); i++) {
                    final VariableElement param = params.get(i);
                    final String declaredType = raw(param.asType().toString());
                    ParamBinder.Binding binding = null;
                    ParamBinder usedBinder = null;
                    String patternKey = null;

                    for (final ParamBinder b : paramBinders) {
                        try {
                            if (b.supports(method, param, processingEnv)) {
                                binding = b.plan(method, param, processingEnv);
                                patternKey = b.patternKey(method, param, processingEnv);
                                usedBinder = b;
                                break;
                            }
                        } catch (final Exception ex) {
                            error(method, "ParamBinder failed on parameter %d: %s", i, ex.getMessage());
                            binding = null;
                            break;
                        }
                    }

                    if (binding == null) {
                        error(method, "No ParamBinder supports parameter %d of type %s in %s#%s.",
                                i, declaredType, ownerFqn, methodName);
                        continue;
                    }

                    final Set<String> guardBits = new TreeSet<>();
                    if (isContextualListener && usedBinder instanceof ContextualParamBinder) {
                        final Map<String, String> providers = ((ContextualParamBinder) usedBinder).requiredProviders(method, param, processingEnv);
                        if (providers != null) {
                            guardBits.addAll(providers.keySet());
                        }
                    } else {
                        guardBits.addAll(binding.guardBits());
                    }

                    Map<String,String> requiredProviders = Collections.emptyMap();
                    if (usedBinder instanceof ProviderAwareBinder paw) {
                        try {
                            requiredProviders = paw.requiredProviders(method, param, processingEnv);
                        } catch (final Exception ignored) {
                        }
                    }

                    if (requiredProviders != null && !requiredProviders.isEmpty()) {
                        for (final var e : requiredProviders.entrySet()) {
                            event.registerGuard(e.getKey(), raw(e.getValue()));
                        }
                    } else if (!guardBits.isEmpty()) {
                        for (final String g : guardBits) {
                            event.registerGuard(g, declaredType);
                        }
                    }

                    final Set<ParamBinder.Extractor> extractors = new LinkedHashSet<>(binding.extractors());
                    for (final ParamBinder.Extractor ex : extractors) {
                        event.registerExtractor(ex.localName(), raw(ex.declaredType()), ex.initExpression());
                    }

                    final ParamPlan plan = new ParamPlan(
                            i - 1,
                            declaredType,
                            nonNull(patternKey, "patternKey"),
                            guardBits,
                            extractors,
                            nonNull(binding.argumentExpression(), "argumentExpression")
                    );
                    paramPlans.add(plan);
                    signatureTypes.add(declaredType);
                }

                final String signatureKey = String.join("|", signatureTypes);
                final Set<String> allGuards = new TreeSet<>();
                final Set<String> allExtractorLocals = new TreeSet<>();
                for (final ParamPlan p : paramPlans) {
                    allGuards.addAll(p.guardBits);
                    for (final ParamBinder.Extractor ex : p.extractors) {
                        allExtractorLocals.add(ex.localName());
                    }
                }

                final String conditionKey = "G:" + String.join(",", allGuards) + ";X:" + String.join(",", allExtractorLocals);
                final ListenerModel lm = new ListenerModel(
                        ownerFqn,
                        methodName,
                        priority,
                        eventFqn,
                        signatureKey,
                        paramPlans
                );

                event.addListener(conditionKey, signatureKey, lm);
            }
        }

        if (events.isEmpty()) {
            return false;
        }

        final List<String> eventOrder = new ArrayList<>(events.keySet());
        Collections.sort(eventOrder);
        final Map<String, Integer> eventIds = new HashMap<>();
        for (int i = 0; i < eventOrder.size(); i++) {
            eventIds.put(eventOrder.get(i), i);
        }

        final LinkedHashMap<String, String> providerDeclTypes = new LinkedHashMap<>();
        for (final EventModel em : events.values()) {
            for (final Map.Entry<String, String> e : em.guardDeclaredTypes.entrySet()) {
                final String name = e.getKey();
                final String type = e.getValue();
                final String prev = providerDeclTypes.putIfAbsent(name, type);
                if (prev != null && !prev.equals(type)) {
                    error(null, "Guard '%s' used with conflicting types: %s vs %s", name, prev, type);
                }
            }
        }

        final Map<String, Integer> providerIds = new LinkedHashMap<>();
        {
            int idx = 0;
            for (final String key : providerDeclTypes.keySet()) {
                providerIds.put(key, idx++);
            }
        }

        if (hadError) {
            return false;
        }

        try {
            emitProviderKeysIfNeeded(providerDeclTypes);
            emitBootstrapAndDispatchers(events, eventOrder, eventIds, providerDeclTypes, providerIds);
        } catch (final IOException ex) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Rokit processor error: " + ex);
        }

        return true;
    }

    private void emitProviderKeysIfNeeded(final LinkedHashMap<String, String> providerDeclTypes) throws IOException {
        if (providerDeclTypes.isEmpty()) {
            return;
        }

        final String fqn = genPkg + ".ProviderKeys";
        JavaFileObject jfo;
        try {
            jfo = filer.createSourceFile(fqn);
        } catch (final FilerException ignore) {
            return;
        }

        try (final Writer w = jfo.openWriter()) {
            w.write("package " + genPkg + ";\n\n");
            w.write("public final class ProviderKeys {\n");
            w.write("    private ProviderKeys() { }\n\n");
            int idx = 0;
            for (final String name : providerDeclTypes.keySet()) {
                final String constName = sanitizeUpper(name);
                w.write("    public static final int " + constName + " = " + idx + ";\n");
                idx++;
            }

            w.write("}\n");
        }
    }


    private void emitBootstrapAndDispatchers(final Map<String, EventModel> events, final List<String> eventOrder, final Map<String, Integer> eventIds, final LinkedHashMap<String, String> providerDeclTypes, final Map<String, Integer> providerIds) throws IOException {
        final String fqn = genPkg + "." + BOOTSTRAP;
        JavaFileObject jfo;
        try {
            jfo = filer.createSourceFile(fqn);
        } catch (final FilerException ignore) {
            return;
        }

        try (final Writer w = jfo.openWriter()) {
            w.write("package " + genPkg + ";\n\n");
            w.write("import bot.staro.rokit.RokitEventBus;\n");
            w.write("import bot.staro.rokit.gen.BusState;\n");
            w.write("import java.util.*;\n");
            w.write("import java.util.concurrent.ConcurrentHashMap;\n");
            w.write("public final class " + BOOTSTRAP + " {\n");
            w.write("    private " + BOOTSTRAP + "() { }\n\n");
            w.write("    private static final class State implements BusState {\n");
            w.write("        final ConcurrentHashMap<Object, java.util.List<Runnable>> removals = new ConcurrentHashMap<>();\n");
            for (final String eventFqn : eventOrder) {
                final String dispatcherName = dispatcherClassName(eventFqn);
                w.write("        final " + dispatcherName + ".Store store_" + sanitizeFqn(eventFqn) + " = new " + dispatcherName + ".Store();\n");
            }

            w.write("        boolean addRemoval(final Object subscriber, final Runnable r) {\n");
            w.write("            return removals.compute(subscriber, (k, v) -> {\n");
            w.write("                if (v == null) { v = new java.util.ArrayList<>(); }\n");
            w.write("                v.add(r);\n");
            w.write("                return v;\n");
            w.write("            }) != null;\n");
            w.write("        }\n");
            w.write("        java.util.List<Runnable> takeRemovals(final Object subscriber) { return removals.remove(subscriber); }\n");
            w.write("        boolean isSubscribed(final Object subscriber) { return removals.containsKey(subscriber); }\n");
            w.write("    }\n\n");

            w.write("    public static BusState newState() { return new State(); }\n\n");

            w.write("    @SuppressWarnings({\"unchecked\", \"rawtypes\"})\n");
            w.write("    public static <E> void dispatch(final RokitEventBus bus, final BusState bs, final E event) {\n");
            if (eventOrder.isEmpty()) {
                w.write("        return;\n");
            } else {
                w.write("        final State s = (State) bs;\n");

                final Map<String, List<EventModel>> eventsByRawType = new LinkedHashMap<>();
                for (EventModel em : events.values()) {
                    eventsByRawType.computeIfAbsent(raw(em.eventFqn), k -> new ArrayList<>()).add(em);
                }

                boolean first = true;
                for (Map.Entry<String, List<EventModel>> entry : eventsByRawType.entrySet()) {
                    final String rawFqn = entry.getKey();
                    final List<EventModel> specializations = entry.getValue();

                    w.write("        " + (first ? "if" : "else if") + " (event instanceof " + rawFqn + ") {\n");
                    first = false;

                    if (specializations.size() == 1 && !specializations.getFirst().isGeneric()) {
                        final EventModel em = specializations.getFirst();
                        w.write("            " + dispatcherClassName(em.eventFqn) + ".dispatch(bus, s.store_" + sanitizeFqn(em.eventFqn) + ", (" + em.eventFqn + ") event);\n");
                    } else {
                        final TypeElement rawElement = elements.getTypeElement(rawFqn);
                        final ExecutableElement accessor = findPayloadAccessor(rawElement);

                        if (accessor == null) {
                            error(rawElement, "Cannot dispatch generic event %s: could not find a suitable payload accessor method like getObject() or getPayload() that returns a type variable.", rawFqn);
                            w.write("            // ERROR: No payload accessor found for " + rawFqn + "\n");
                            w.write("        }\n");
                            continue;
                        }

                        w.write("            final " + rawFqn + " typedEvent = (" + rawFqn + ") event;\n");
                        w.write("            final Object payload = typedEvent." + accessor.getSimpleName() + "();\n");

                        for (EventModel em : specializations) {
                            w.write("            if (payload instanceof " + em.getGenericArgumentFqn() + ") {\n");
                            w.write("                " + dispatcherClassName(em.eventFqn) + ".dispatch(bus, s.store_" + sanitizeFqn(em.eventFqn) + ", typedEvent);\n");
                            w.write("            }\n");
                        }
                    }
                    w.write("            return;\n");
                    w.write("        }\n");
                }
                w.write("        else { return; }\n");
            }
            w.write("    }\n\n");

            w.write("    public static void register(final RokitEventBus bus, final BusState bs, final Object subscriber) {\n");
            w.write("        final State s = (State) bs;\n");
            final List<String> owners = collectOwners(events);
            boolean firstOwner = true;
            for (final String owner : owners) {
                w.write("        " + (firstOwner ? "if" : "else if") + " (subscriber instanceof " + owner + " listenerInstance) {\n");
                firstOwner = false;
                for (final Map.Entry<String, EventModel> eventEntry : events.entrySet()) {
                    final String eventFqn = eventEntry.getKey();
                    final EventModel em = eventEntry.getValue();
                    final List<BucketKey> keys = em.orderedBucketKeys();

                    for (final BucketKey bk : keys) {
                        final List<ListenerModel> listeners = em.buckets.get(bk);
                        for (final ListenerModel lm : listeners) {
                            if (!lm.ownerFqn.equals(owner)) {
                                continue;
                            }

                            final long sid = stableId(lm.ownerFqn, lm.methodName, lm.eventFqn, lm.signatureKey);
                            final String dispatcher = dispatcherClassName(eventFqn);
                            final String invokerInterface = dispatcherInvokerInterfaceName(lm.eventFqn, lm.signatureKey);
                            final String addMethod = dispatcherAddMethodName(lm.eventFqn, bk, lm.signatureKey);
                            final String args = buildInvokerArgs(lm.paramPlans);
                            w.write("            {\n");
                            w.write("                final " + dispatcher + "." + invokerInterface + " adapter = new " + dispatcher + "." + invokerInterface + "() {\n");
                            w.write("                    @Override public void invoke(final " + raw(lm.eventFqn) + " event" + buildParamDeclsFromPlans(lm.paramPlans) + ") {\n");
                            w.write("                        listenerInstance." + lm.methodName + "((" + lm.eventFqn + ")event" + args + ");\n");
                            w.write("                    }\n");
                            w.write("                };\n");
                            w.write("                final Runnable removal = " + dispatcher + "." + addMethod + "(bus, s.store_" + sanitizeFqn(eventFqn) + ", listenerInstance, " + lm.priority + ", " + sid + "L, adapter);\n");
                            w.write("                s.addRemoval(subscriber, removal);\n");
                            w.write("            }\n");
                        }
                    }
                }

                w.write("        }\n");
            }
            if (!owners.isEmpty()) {
                w.write("        else { }\n");
            }
            w.write("    }\n\n");

            w.write("    public static void unregister(final RokitEventBus bus, final BusState bs, final Object subscriber) {\n");
            w.write("        final State s = (State) bs;\n");
            w.write("        final java.util.List<Runnable> list = s.takeRemovals(subscriber);\n");
            w.write("        if (list == null) { return; }\n");
            w.write("        for (int i = 0; i < list.size(); i++) { list.get(i).run(); }\n");
            w.write("    }\n\n");

            w.write("    public static boolean isSubscribed(final BusState bs, final Object subscriber) {\n");
            w.write("        final State s = (State) bs;\n");
            w.write("        return s.isSubscribed(subscriber);\n");
            w.write("    }\n\n");

            for (final String eventFqn : eventOrder) {
                final EventModel em = events.get(eventFqn);
                emitEventDispatcher(w, em, providerDeclTypes, providerIds);
            }

            w.write("}\n");
        }
    }

    private void emitEventDispatcher(final Writer w,
                                     final EventModel em,
                                     final LinkedHashMap<String, String> providerDeclTypes,
                                     final Map<String, Integer> providerIds) throws IOException {
        final String className = dispatcherClassName(em.eventFqn);
        w.write("    static final class " + className + " {\n");
        w.write("        static final class Store { \n");

        final List<BucketKey> keys = em.orderedBucketKeys();
        for (final BucketKey key : keys) {
            final String sig = key.signatureKey;
            final String iface = dispatcherInvokerInterfaceName(em.eventFqn, sig);
            final String baseName = bucketFieldBase(em.eventFqn, key);
            w.write("            volatile " + iface + "[] " + baseName + " = null;\n");
            w.write("            volatile int[] " + baseName + "_P = null;\n");
            w.write("            volatile long[] " + baseName + "_I = null;\n");
        }

        w.write("        }\n\n");

        final List<String> signatures = new ArrayList<>(em.signatures);
        Collections.sort(signatures);
        for (final String sig : signatures) {
            final String iface = dispatcherInvokerInterfaceName(em.eventFqn, sig);
            final ListenerModel representative = findListenerBySignature(em, sig);
            w.write("        interface " + iface + " { void invoke(final " + raw(em.eventFqn) + " event" + buildParamDeclsFromPlans(representative.paramPlans) + "); }\n");
        }

        w.write("\n");

        for (final BucketKey key : keys) {
            final String sig = key.signatureKey;
            final String iface = dispatcherInvokerInterfaceName(em.eventFqn, sig);
            final String baseName = bucketFieldBase(em.eventFqn, key);
            final String addName = dispatcherAddMethodName(em.eventFqn, key, sig);
            final String removeName = dispatcherRemoveMethodName(em.eventFqn, key, sig);

            w.write("        static Runnable " + addName + "(final RokitEventBus bus, final Store store, final Object subscriber, final int priority, final long id, final " + iface + " inv) {\n");
            w.write("            synchronized (store) {\n");
            w.write("                final " + iface + "[] prev = store." + baseName + ";\n");
            w.write("                final int[] prevP = store." + baseName + "_P;\n");
            w.write("                final long[] prevI = store." + baseName + "_I;\n");
            w.write("                final int n = prev == null ? 0 : prev.length;\n");
            w.write("                if (n != 0) {\n");
            w.write("                    for (int i = 0; i < n; i++) {\n");
            w.write("                        if (prevI[i] == id) {\n");
            w.write("                            return new Runnable() { @Override public void run() { } };\n");
            w.write("                        }\n");
            w.write("                    }\n");
            w.write("                }\n");
            w.write("                final int pos = findInsertPos(prevP, n, priority);\n");
            w.write("                final " + iface + "[] next = new " + iface + "[n + 1];\n");
            w.write("                final int[] nextP = new int[n + 1];\n");
            w.write("                final long[] nextI = new long[n + 1];\n");
            w.write("                if (n != 0) {\n");
            w.write("                    System.arraycopy(prev, 0, next, 0, pos);\n");
            w.write("                    System.arraycopy(prevP, 0, nextP, 0, pos);\n");
            w.write("                    System.arraycopy(prevI, 0, nextI, 0, pos);\n");
            w.write("                    System.arraycopy(prev, pos, next, pos + 1, n - pos);\n");
            w.write("                    System.arraycopy(prevP, pos, nextP, pos + 1, n - pos);\n");
            w.write("                    System.arraycopy(prevI, pos, nextI, pos + 1, n - pos);\n");
            w.write("                }\n");
            w.write("                next[pos] = inv;\n");
            w.write("                nextP[pos] = priority;\n");
            w.write("                nextI[pos] = id;\n");
            w.write("                store." + baseName + " = next;\n");
            w.write("                store." + baseName + "_P = nextP;\n");
            w.write("                store." + baseName + "_I = nextI;\n");
            w.write("            }\n");
            w.write("            return new Runnable() { @Override public void run() { " + removeName + "(store, inv); } };\n");
            w.write("        }\n");

            w.write("        static void " + removeName + "(final Store store, final " + iface + " inv) {\n");
            w.write("            synchronized (store) {\n");
            w.write("                final " + iface + "[] prev = store." + baseName + ";\n");
            w.write("                final int[] prevP = store." + baseName + "_P;\n");
            w.write("                final long[] prevI = store." + baseName + "_I;\n");
            w.write("                if (prev == null) { return; }\n");
            w.write("                final int n = prev.length;\n");
            w.write("                int idx = -1;\n");
            w.write("                for (int i = 0; i < n; i++) { if (prev[i] == inv) { idx = i; break; } }\n");
            w.write("                if (idx < 0) { return; }\n");
            w.write("                if (n == 1) { store." + baseName + " = null; store." + baseName + "_P = null; store." + baseName + "_I = null; return; }\n");
            w.write("                final " + iface + "[] next = new " + iface + "[n - 1];\n");
            w.write("                final int[] nextP = new int[n - 1];\n");
            w.write("                final long[] nextI = new long[n - 1];\n");
            w.write("                System.arraycopy(prev, 0, next, 0, idx);\n");
            w.write("                System.arraycopy(prevP, 0, nextP, 0, idx);\n");
            w.write("                System.arraycopy(prevI, 0, nextI, 0, idx);\n");
            w.write("                System.arraycopy(prev, idx + 1, next, idx, n - idx - 1);\n");
            w.write("                System.arraycopy(prevP, idx + 1, nextP, idx, n - idx - 1);\n");
            w.write("                System.arraycopy(prevI, idx + 1, nextI, idx, n - idx - 1);\n");
            w.write("                store." + baseName + " = next;\n");
            w.write("                store." + baseName + "_P = nextP;\n");
            w.write("                store." + baseName + "_I = nextI;\n");
            w.write("            }\n");
            w.write("        }\n\n");
        }

        w.write("        private static int findInsertPos(final int[] priorities, final int n, final int p) {\n");
        w.write("            if (n == 0 || priorities == null) return 0;\n");
        w.write("            int lo = 0, hi = n;\n");
        w.write("            while (lo < hi) {\n");
        w.write("                final int mid = (lo + hi) >>> 1;\n");
        w.write("                final int mp = priorities[mid];\n");
        w.write("                if (mp > p) lo = mid + 1; else hi = mid;\n");
        w.write("            }\n");
        w.write("            return lo;\n");
        w.write("        }\n\n");

        w.write("        static void dispatch(final RokitEventBus bus, final Store store, final " + raw(em.eventFqn) + " event) {\n");

        final Set<String> allGuardsForEvent = new LinkedHashSet<>();
        final Set<String> allExtractorsForEvent = new LinkedHashSet<>();
        for (final List<ListenerModel> bucket : em.buckets.values()) {
            for (final ListenerModel listener : bucket) {
                for (final ParamPlan plan : listener.paramPlans) {
                    allGuardsForEvent.addAll(plan.guardBits);
                    for (final ParamBinder.Extractor ex : plan.extractors) {
                        allExtractorsForEvent.add(ex.localName());
                    }
                }
            }
        }

        for (final String g : allGuardsForEvent) {
            final String type = em.guardDeclaredTypes.get(g);
            final String constName = sanitizeUpper(g);
            w.write("            final " + type + " " + sanitizeLower(g) + " = " + genPkg + ".ProviderKeys." + constName + " >= 0 ? bus.<" + type + ">getProvider(" + genPkg + ".ProviderKeys." + constName + ") : null;\n");
        }
        for (final String local : allExtractorsForEvent) {
            final ExtractorModel ex = em.extractors.get(local);
            if (ex != null) {
                w.write("            final " + ex.declaredType + " " + ex.localName + " = " + ex.initExpression + ";\n");
            }
        }

        final Map<String, List<BucketKey>> byCond = new LinkedHashMap<>();
        for (final BucketKey bk : keys) {
            byCond.computeIfAbsent(bk.conditionKey, k -> new ArrayList<>()).add(bk);
        }

        List<Map.Entry<String, List<BucketKey>>> sortedEntries = new ArrayList<>(byCond.entrySet());
        sortedEntries.sort((e1, e2) -> {
            int maxPriority1 = e1.getValue().stream()
                    .flatMap(bk -> em.buckets.get(bk).stream())
                    .mapToInt(ListenerModel::priority)
                    .max().orElse(Integer.MIN_VALUE);
            int maxPriority2 = e2.getValue().stream()
                    .flatMap(bk -> em.buckets.get(bk).stream())
                    .mapToInt(ListenerModel::priority)
                    .max().orElse(Integer.MIN_VALUE);
            return Integer.compare(maxPriority2, maxPriority1);
        });

        for (final Map.Entry<String, List<BucketKey>> entry : sortedEntries) {
            final String condKey = entry.getKey();
            final List<BucketKey> group = entry.getValue();

            final String condExpr = renderCondition(new BucketKey(condKey, ""), em.guardDeclaredTypes.keySet(), em.extractors.keySet());
            w.write("            " + (condExpr.isEmpty() ? "" : "if (" + condExpr + ") ") + "{\n");

            for (final BucketKey bk : group) {
                final String sig = bk.signatureKey;
                final ListenerModel representative = em.buckets.get(bk).getFirst();
                final String invokeArgs = buildInvokerArgs(representative.paramPlans);
                final String iface = dispatcherInvokerInterfaceName(em.eventFqn, sig);
                final String baseName = bucketFieldBase(em.eventFqn, bk);
                w.write("                {\n");
                w.write("                    final " + iface + "[] local = store." + baseName + ";\n");
                w.write("                    if (local != null) {\n");
                w.write("                        for (int index = 0; index < local.length; index++) {\n");
                w.write("                            local[index].invoke(event" + invokeArgs + ");\n");
                w.write("                        }\n");
                w.write("                    }\n");
                w.write("                }\n");
            }

            w.write("            }\n");
        }

        w.write("        }\n");
        w.write("    }\n");
    }

    private boolean isCandidateName(final String name) {
        for (final String candidate : PAYLOAD_ACCESSOR_CANDIDATES) {
            if (candidate.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private ExecutableElement findPayloadAccessor(final TypeElement eventElement) {
        if (eventElement.getKind() == ElementKind.RECORD) {
            for (RecordComponentElement component : eventElement.getRecordComponents()) {
                if (isOrContainsTypeVar(component.asType())) {
                    return component.getAccessor();
                }
            }
        }

        for (final Element member : elements.getAllMembers(eventElement)) {
            if (member.getKind() != ElementKind.METHOD || !(member instanceof ExecutableElement accessor)) {
                continue;
            }
            if (!isCandidateName(accessor.getSimpleName().toString()) || !accessor.getModifiers().contains(Modifier.PUBLIC) || !accessor.getParameters().isEmpty()) {
                continue;
            }
            if (isOrContainsTypeVar(accessor.getReturnType())) {
                return accessor;
            }
        }
        return null;
    }

    private boolean isOrContainsTypeVar(TypeMirror type) {
        if (type.getKind() == TypeKind.TYPEVAR) {
            return true;
        }
        if (type instanceof DeclaredType) {
            for (TypeMirror typeArgument : ((DeclaredType) type).getTypeArguments()) {
                if (isOrContainsTypeVar(typeArgument)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String sanitizeFqn(final String fqn) {
        return fqn.replaceAll("[^A-Za-z0-9_.]", "_").replace('.', '_');
    }

    private static String dispatcherClassName(final String eventFqn) {
        return "Dispatch_" + sanitizeFqn(eventFqn);
    }

    private static String dispatcherInvokerInterfaceName(final String eventFqn, final String signatureKey) {
        return "Invoker_" + Long.toHexString(fnv1a(eventFqn + "##" + signatureKey));
    }

    private static String dispatcherAddMethodName(final String eventFqn, final BucketKey key, final String sig) {
        return "add_" + Long.toHexString(fnv1a(eventFqn + "##" + key.toString() + "##" + sig));
    }

    private static String dispatcherRemoveMethodName(final String eventFqn, final BucketKey key, final String sig) {
        return "remove_" + Long.toHexString(fnv1a(eventFqn + "##" + key.toString() + "##" + sig));
    }

    private static String bucketFieldBase(final String eventFqn, final BucketKey key) {
        return "BUCKET_" + Long.toHexString(fnv1a(eventFqn + "##" + key.toString()));
    }

    private static String hashKey(final BucketKey key) {
        return Long.toHexString(fnv1a(key.conditionKey + "|" + key.signatureKey));
    }

    private static String sigHash(final String sig) {
        return Long.toHexString(fnv1a(sig));
    }

    private static long fnv1a(final String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }

        return h;
    }

    private static String buildParamDeclsFromPlans(final List<ParamPlan> plans) {
        if (plans.isEmpty()) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        for (final ParamPlan p : plans) {
            sb.append(", final ").append(p.declaredType).append(" ").append(p.argExpr);
        }
        return sb.toString();
    }


    private static String renderSignatureParams(final List<ParamPlan> plans) {
        if (plans.isEmpty()) {
            return "";
        }

        final StringBuilder sb = new StringBuilder();
        for (final ParamPlan p : plans) {
            sb.append(", ").append(p.declaredType).append(" ").append(paramVarName(p));
        }

        return sb.toString();
    }

    private static String buildInvokerArgs(final List<ParamPlan> plans) {
        if (plans.isEmpty()) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        for (final ParamPlan p : plans) {
            sb.append(", ").append(p.argExpr);
        }
        return sb.toString();
    }


    private static String renderCondition(final BucketKey key, final Set<String> allGuards, final Set<String> allExtractors) {
        final int gi = key.conditionKey.indexOf("G:");
        final int xi = key.conditionKey.indexOf(";X:");
        final String guards = gi >= 0 && xi > gi ? key.conditionKey.substring(gi + 2, xi) : "";
        final String exts = xi >= 0 ? key.conditionKey.substring(xi + 3) : "";
        final List<String> terms = new ArrayList<>();
        if (!guards.isEmpty()) {
            for (final String g : guards.split(",")) {
                if (!g.isEmpty()) {
                    terms.add(sanitizeLower(g) + " != null");
                }
            }
        }

        if (!exts.isEmpty()) {
            for (final String x : exts.split(",")) {
                if (!x.isEmpty()) {
                    terms.add(x + " != null");
                }
            }
        }

        return String.join(" && ", terms);
    }

    private static String paramVarName(final ParamPlan p) {
        final String expr = p.argExpr.trim();
        if (expr.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            return expr;
        }

        return sanitizeLower(simpleName(p.declaredType));
    }

    private static String simpleName(final String fqn) {
        final int idx = fqn.lastIndexOf('.');
        return idx < 0 ? fqn : fqn.substring(idx + 1);
    }

    private static String raw(final String fqn) {
        final int idx = fqn.indexOf('<');
        return idx < 0 ? fqn : fqn.substring(0, idx);
    }

    private static String sanitizeUpper(final String name) {
        final String s = name.replaceAll("[^A-Za-z0-9]+", "_");
        return s.toUpperCase(Locale.ROOT);
    }

    private static String sanitizeLower(final String name) {
        final String s = name.replaceAll("[^A-Za-z0-9]+", "_");
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static String nonNull(final String value, final String label) {
        if (value == null) {
            throw new IllegalStateException(label + " is null");
        }

        return value;
    }

    private void error(final Element where, final String format, final Object... args) {
        final String msg = String.format(Locale.ROOT, format, args);
        if (where != null) {
            messager.printMessage(Diagnostic.Kind.ERROR, msg, where);
        } else {
            messager.printMessage(Diagnostic.Kind.ERROR, msg);
        }

        hadError = true;
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

    private static long stableId(final String ownerFqn, final String methodName, final String eventFqn, final String signatureKey) {
        final String s = ownerFqn + "#" + methodName + "(" + eventFqn + "|" + signatureKey + ")";
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }

        return h;
    }

    private static List<String> collectOwners(final Map<String, EventModel> events) {
        final LinkedHashSet<String> owners = new LinkedHashSet<>();
        for (final EventModel em : events.values()) {
            for (final List<ListenerModel> list : em.buckets.values()) {
                for (final ListenerModel lm : list) {
                    owners.add(lm.ownerFqn);
                }
            }
        }

        return new ArrayList<>(owners);
    }

    private ListenerModel findListenerBySignature(EventModel em, String signature) {
        for (List<ListenerModel> bucket : em.buckets.values()) {
            for (ListenerModel listener : bucket) {
                if (listener.signatureKey.equals(signature)) {
                    return listener;
                }
            }
        }
        return null; // Should not happen
    }

    private record MethodInfo(TypeElement annotation, ExecutableElement method) {}

    private record ParamPlan(int extraIndex, String declaredType, String patternKey, Set<String> guardBits, Set<ParamBinder.Extractor> extractors, String argExpr) {}

    private record ListenerModel(String ownerFqn, String methodName, int priority, String eventFqn, String signatureKey, List<ParamPlan> paramPlans) {}

    private record ExtractorModel(String localName, String declaredType, String initExpression) {}

    private record BucketKey(String conditionKey, String signatureKey) {}

    private static final class EventModel {
        final String eventFqn;
        final Map<String, String> guardDeclaredTypes = new LinkedHashMap<>();
        final Map<String, ExtractorModel> extractors = new LinkedHashMap<>();
        final Set<String> signatures = new LinkedHashSet<>();
        final Map<BucketKey, List<ListenerModel>> buckets = new LinkedHashMap<>();

        EventModel(final String eventFqn) {
            this.eventFqn = eventFqn;
        }

        boolean isGeneric() {
            return eventFqn.indexOf('<') >= 0;
        }

        String getGenericArgumentFqn() {
            final int start = eventFqn.indexOf('<');
            final int end = eventFqn.lastIndexOf('>');
            if (start >= 0 && end > start) {
                String arg = eventFqn.substring(start + 1, end);
                if (arg.equals("?")) {
                    return "java.lang.Object";
                }
                return arg;
            }
            return "java.lang.Object";
        }

        void registerGuard(final String name, final String declaredType) {
            final String prev = guardDeclaredTypes.putIfAbsent(name, declaredType);
            if (prev != null && !prev.equals(declaredType)) {
                throw new IllegalStateException("Guard " + name + " has conflicting types: " + prev + " vs " + declaredType);
            }
        }

        void registerExtractor(final String localName, final String declaredType, final String initExpr) {
            final ExtractorModel prev = extractors.putIfAbsent(localName, new ExtractorModel(localName, declaredType, initExpr));
            if (prev != null) {
                if (!prev.declaredType.equals(declaredType) || !prev.initExpression.equals(initExpr)) {
                    throw new IllegalStateException("Extractor " + localName + " mismatch.");
                }
            }
        }

        void addListener(final String conditionKey, final String signatureKey, final ListenerModel lm) {
            signatures.add(signatureKey);
            final BucketKey key = new BucketKey(conditionKey, signatureKey);
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(lm);
            buckets.get(key).sort((a, b) -> Integer.compare(b.priority, a.priority));
        }

        List<BucketKey> orderedBucketKeys() {
            final List<BucketKey> out = new ArrayList<>(buckets.keySet());
            out.sort(Comparator.comparing((BucketKey k) -> k.signatureKey).thenComparing(k -> k.conditionKey));
            return out;
        }
    }

}
