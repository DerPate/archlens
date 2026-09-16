package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.build.BuildModule;
import dev.dominikbreu.archlens.extractor.sourcefacts.SourceFactIndex;
import dev.dominikbreu.archlens.extractor.sourcefacts.SourceMethod;
import dev.dominikbreu.archlens.extractor.sourcefacts.SourceType;
import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.Component;
import dev.dominikbreu.archlens.model.SourceInfo;
import dev.dominikbreu.archlens.model.TransactionPolicy;
import dev.dominikbreu.archlens.model.ids.AppId;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Resolves safely targetable Spring and EJB XML transaction method rules. */
final class TransactionXmlPolicyResolver {

    private static final Pattern EXECUTION =
            Pattern.compile("execution\\s*\\([^\\s]+\\s+([\\w.$*]+)\\.([\\w$*]+)\\s*\\(.*");

    void apply(BuildModule module, SourceFactIndex facts, ArchitectureModel model, AppId appId) {
        for (Path path : xmlFiles(module)) {
            Document document = parse(path);
            if (document == null) continue;
            if ("ejb-jar.xml".equalsIgnoreCase(fileName(path))) {
                applyEjbDescriptor(path, document, facts, model, appId);
            } else {
                applySpringContext(path, document, facts, model, appId);
            }
        }
    }

    /** Applies Spring advice rules to methods matched by safely parsed execution pointcuts. */
    private void applySpringContext(
            Path path, Document document, SourceFactIndex facts, ArchitectureModel model, AppId appId) {
        Map<String, List<XmlRule>> rulesByAdvice = new LinkedHashMap<>();
        Map<String, String> pointcuts = new LinkedHashMap<>();
        for (Element pointcut : descendants(document.getDocumentElement(), "pointcut")) {
            String id = pointcut.getAttribute("id");
            String expression = pointcut.getAttribute("expression");
            if (!id.isBlank() && !expression.isBlank()) pointcuts.put(id, expression);
        }
        for (Element advice : descendants(document.getDocumentElement(), "advice")) {
            String id = advice.getAttribute("id");
            if (id.isBlank()) continue;
            List<XmlRule> rules = descendants(advice, "method").stream()
                    .map(method -> new XmlRule(
                            value(method, "name", "*"),
                            normalize(value(method, "propagation", "REQUIRED")),
                            value(method, "propagation", "REQUIRED"),
                            nullableBoolean(method.getAttribute("read-only")),
                            blankToNull(method.getAttribute("isolation")),
                            rollbackRules(method)))
                    .toList();
            if (!rules.isEmpty()) rulesByAdvice.put(id, rules);
        }
        for (Element advisor : descendants(document.getDocumentElement(), "advisor")) {
            String adviceRef = advisor.getAttribute("advice-ref");
            List<XmlRule> rules = rulesByAdvice.get(adviceRef);
            if (rules == null) continue;
            String pointcut = advisor.getAttribute("pointcut");
            if (pointcut.isBlank()) pointcut = pointcuts.get(advisor.getAttribute("pointcut-ref"));
            Target target = target(pointcut);
            if (target == null) {
                addUnresolved(model, appId, path, adviceRef, rules.getFirst());
                continue;
            }
            boolean applied = false;
            for (SourceType type : facts.types()) {
                if (!glob(target.typePattern(), type.qualifiedName())) continue;
                Component component = component(model, appId, type.qualifiedName());
                if (component == null) continue;
                for (SourceMethod method : facts.methods(type.id())) {
                    if (!glob(target.methodPattern(), method.name())) continue;
                    XmlRule rule = firstRule(rules, method.name());
                    if (rule != null) {
                        upsert(model, xmlPolicy(component, appId, method, rule, path, "spring-xml", 0.9));
                        applied = true;
                    }
                }
            }
            if (!applied) addUnresolved(model, appId, path, adviceRef, rules.getFirst());
        }
    }

    /** Applies EJB container-transaction declarations to matching bean methods and parameter signatures. */
    private void applyEjbDescriptor(
            Path path, Document document, SourceFactIndex facts, ArchitectureModel model, AppId appId) {
        for (Element transaction : descendants(document.getDocumentElement(), "container-transaction")) {
            String nativePolicy = childText(transaction, "trans-attribute");
            String policy = normalize(nativePolicy);
            for (Element methodElement : children(transaction, "method")) {
                String ejbName = childText(methodElement, "ejb-name");
                String methodName = Objects.toString(childText(methodElement, "method-name"), "*");
                for (Component component : model.components.stream()
                        .filter(value -> appId.equals(value.module)
                                && ((value.ejbName != null && value.ejbName.equals(ejbName))
                                        || value.name.equals(ejbName)
                                        || value.qualifiedName.equals(ejbName)))
                        .toList()) {
                    SourceType type = facts.type(component.qualifiedName);
                    if (type == null) continue;
                    List<String> parameterTypes = children(methodElement, "method-params").stream()
                            .flatMap(params -> children(params, "method-param").stream())
                            .map(Element::getTextContent)
                            .map(String::strip)
                            .toList();
                    for (SourceMethod method : facts.methods(type.id())) {
                        if (!glob(methodName, method.name()) || !parametersMatch(parameterTypes, method)) continue;
                        XmlRule rule = new XmlRule(methodName, policy, nativePolicy, null, null, List.of());
                        upsert(model, xmlPolicy(component, appId, method, rule, path, "ejb-xml", 1.0));
                    }
                }
            }
        }
    }

    /** Builds a method-bound transaction policy from a resolved XML rule. */
    private static TransactionPolicy xmlPolicy(
            Component component,
            AppId appId,
            SourceMethod method,
            XmlRule rule,
            Path path,
            String framework,
            double confidence) {
        TransactionPolicy policy = new TransactionPolicy();
        policy.id = "transaction-boundary:" + component.id.serialize() + "#" + method.signature();
        policy.appId = appId;
        policy.componentId = component.id;
        policy.methodName = method.name();
        policy.methodSignature = method.signature();
        policy.framework = framework;
        policy.policy = rule.policy();
        policy.nativePolicy = rule.nativePolicy();
        policy.readOnly = rule.readOnly();
        policy.isolation = rule.isolation();
        policy.rollbackRules.addAll(rule.rollbackRules());
        policy.declarationLevel = "xml";
        policy.source = new SourceInfo(path.toAbsolutePath().toString(), 0, framework, confidence);
        return policy;
    }

    /** Preserves unmatched Spring advice as a lower-confidence unresolved configuration policy. */
    private static void addUnresolved(ArchitectureModel model, AppId appId, Path path, String adviceId, XmlRule rule) {
        TransactionPolicy policy = new TransactionPolicy();
        policy.id = "transaction-config:" + appId.serialize() + ":" + adviceId;
        policy.appId = appId;
        policy.methodName = rule.methodPattern();
        policy.methodSignature = rule.methodPattern();
        policy.framework = "spring-xml";
        policy.policy = rule.policy();
        policy.nativePolicy = rule.nativePolicy();
        policy.declarationLevel = "xml-unresolved";
        policy.limitations.add("unresolved-aop-pointcut");
        policy.source = new SourceInfo(path.toAbsolutePath().toString(), 0, "spring-xml", 0.5);
        model.transactionPolicies.add(policy);
    }

    /** Replaces a policy by stable identifier and marks XML overlap with a nondefault annotation policy. */
    private static void upsert(ArchitectureModel model, TransactionPolicy candidate) {
        TransactionPolicy existing = model.transactionPolicies.stream()
                .filter(policy -> candidate.id.equals(policy.id))
                .findFirst()
                .orElse(null);
        if (existing != null && "spring-xml".equals(candidate.framework) && !existing.defaulted) {
            candidate.limitations.add("coexisting-annotation-and-xml-advice");
            if (candidate.source != null) candidate.source.confidence = Math.min(candidate.source.confidence, 0.6);
        }
        model.transactionPolicies.removeIf(policy -> candidate.id.equals(policy.id));
        model.transactionPolicies.add(candidate);
    }

    /** Extracts type and method globs from a supported Spring {@code execution(...)} expression. */
    private static Target target(String pointcut) {
        Matcher matcher = EXECUTION.matcher(Objects.toString(pointcut, ""));
        return matcher.matches() ? new Target(matcher.group(1), matcher.group(2)) : null;
    }

    /** Selects the matching method rule with the most literal pattern characters. */
    private static XmlRule firstRule(List<XmlRule> rules, String method) {
        return rules.stream()
                .filter(rule -> glob(rule.methodPattern(), method))
                .max(Comparator.comparingInt(rule -> specificity(rule.methodPattern())))
                .orElse(null);
    }

    /** Scores a glob by its non-wildcard character count. */
    private static int specificity(String pattern) {
        return pattern == null ? 0 : pattern.replace("*", "").length();
    }

    /** Matches optional EJB descriptor parameter types by qualified or simple name. */
    private static boolean parametersMatch(List<String> descriptorTypes, SourceMethod method) {
        if (descriptorTypes.isEmpty()) return true;
        if (descriptorTypes.size() != method.parameterTypes().size()) return false;
        for (int index = 0; index < descriptorTypes.size(); index++) {
            String expected = descriptorTypes.get(index);
            String actual = method.parameterTypes().get(index);
            if (!expected.equals(actual) && !simpleName(expected).equals(simpleName(actual))) return false;
        }
        return true;
    }

    /** Removes package and nested-class prefixes from a type name. */
    private static String simpleName(String type) {
        int separator = type != null ? Math.max(type.lastIndexOf('.'), type.lastIndexOf('$')) : -1;
        return separator >= 0 ? type.substring(separator + 1) : Objects.toString(type, "");
    }

    /** Matches a value against a literal glob whose only wildcard is {@code *}. */
    private static boolean glob(String pattern, String value) {
        if (pattern == null || value == null) return false;
        String regex = "\\Q" + pattern.replace("*", "\\E.*\\Q") + "\\E";
        return value.matches(regex);
    }

    /** Finds distinct resource XML files up to depth eight, excluding persistence descriptors. */
    private static List<Path> xmlFiles(BuildModule module) {
        List<Path> result = new ArrayList<>();
        for (File root : module.resourceRoots()) {
            if (!root.isDirectory()) continue;
            try (var paths = Files.find(root.toPath(), 8, (path, attrs) -> attrs.isRegularFile())) {
                paths.filter(path -> fileName(path).toLowerCase(Locale.ROOT).endsWith(".xml"))
                        .filter(path -> !"persistence.xml".equalsIgnoreCase(fileName(path)))
                        .forEach(result::add);
            } catch (IOException _) {
            }
        }
        return result.stream().distinct().sorted().toList();
    }

    /** Parses XML with DTD, external entity, and external schema access disabled. */
    private static Document parse(Path path) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            try (InputStream input = Files.newInputStream(path)) {
                return factory.newDocumentBuilder().parse(input);
            }
        } catch (IOException | ParserConfigurationException | SAXException | IllegalArgumentException _) {
            return null;
        }
    }

    /** Returns namespace-agnostic descendant elements with the requested local name. */
    private static List<Element> descendants(Element root, String localName) {
        List<Element> result = new ArrayList<>();
        NodeList list = root.getElementsByTagNameNS("*", localName);
        for (int i = 0; i < list.getLength(); i++) if (list.item(i) instanceof Element e) result.add(e);
        return result;
    }

    /** Returns direct child elements with the requested local name. */
    private static List<Element> children(Element root, String localName) {
        List<Element> result = new ArrayList<>();
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element e && localName.equals(e.getLocalName())) result.add(e);
        }
        return result;
    }

    /** Returns stripped text from the first matching direct child. */
    private static String childText(Element root, String localName) {
        return children(root, localName).stream()
                .findFirst()
                .map(e -> e.getTextContent().strip())
                .orElse(null);
    }

    /** Reads a nonblank attribute or returns its fallback. */
    private static String value(Element element, String name, String fallback) {
        String value = element.getAttribute(name);
        return value.isBlank() ? fallback : value;
    }

    /** Preserves declared rollback and no-rollback attributes as named rule strings. */
    private static List<String> rollbackRules(Element method) {
        List<String> result = new ArrayList<>();
        for (String name : List.of("rollback-for", "no-rollback-for")) {
            if (!method.getAttribute(name).isBlank()) result.add(name + "=" + method.getAttribute(name));
        }
        return result;
    }

    /** Normalizes Spring and EJB policy spellings to canonical transaction attribute names. */
    private static String normalize(String value) {
        if (value == null || value.isBlank()) return "REQUIRED";
        String normalized = value.replace("_", "").replace("-", "").toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "REQUIRESNEW" -> "REQUIRES_NEW";
            case "NOTSUPPORTED" -> "NOT_SUPPORTED";
            default -> normalized;
        };
    }

    /** Parses a nonblank Boolean while preserving an unspecified value as {@code null}. */
    private static Boolean nullableBoolean(String value) {
        return value == null || value.isBlank() ? null : Boolean.valueOf(value);
    }

    /** Converts blank configuration text to {@code null}. */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Finds a component by application and qualified type name. */
    private static Component component(ArchitectureModel model, AppId appId, String qualifiedName) {
        return model.components.stream()
                .filter(value -> appId.equals(value.module) && qualifiedName.equals(value.qualifiedName))
                .findFirst()
                .orElse(null);
    }

    /** Returns a path's file name or an empty string when unavailable. */
    private static String fileName(Path path) {
        Path fileName = path != null ? path.getFileName() : null;
        return fileName != null ? fileName.toString() : "";
    }

    /**
     * Normalized transaction attributes extracted from one XML method rule.
     *
     * @param methodPattern method-name glob
     * @param policy canonical transaction policy
     * @param nativePolicy original descriptor spelling
     * @param readOnly optional Spring read-only flag
     * @param isolation optional isolation declaration
     * @param rollbackRules rollback and no-rollback declarations
     */
    private record XmlRule(
            String methodPattern,
            String policy,
            String nativePolicy,
            Boolean readOnly,
            String isolation,
            List<String> rollbackRules) {}

    /**
     * Type and method globs extracted from a supported pointcut.
     *
     * @param typePattern qualified type glob
     * @param methodPattern method-name glob
     */
    private record Target(String typePattern, String methodPattern) {}
}
