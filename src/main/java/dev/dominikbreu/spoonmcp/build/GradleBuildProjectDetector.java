package dev.dominikbreu.spoonmcp.build;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GradleBuildProjectDetector implements BuildProjectDetector {

    private static final String JAVA_LIBRARY = "java-library";

    private static final Pattern INCLUDE_QUOTED = Pattern.compile("[\"']:?([^\"']+)[\"']");
    private static final Pattern GROOVY_PLUGIN = Pattern.compile("id\\s+[\"']([^\"']+)[\"']");
    private static final Pattern KOTLIN_PLUGIN = Pattern.compile("id\\([\"']([^\"']+)[\"']\\)");

    @Override
    public Optional<BuildProject> detect(File root) {
        boolean groovy = new File(root, "settings.gradle").isFile() || new File(root, "build.gradle").isFile();
        boolean kotlin = new File(root, "settings.gradle.kts").isFile() || new File(root, "build.gradle.kts").isFile();
        if (!groovy && !kotlin) return Optional.empty();
        BuildSystem system;

        if (groovy && kotlin) {
            system = BuildSystem.MIXED;
        } else {
            system = kotlin ? BuildSystem.GRADLE_KOTLIN : BuildSystem.GRADLE_GROOVY;
        }
        List<String> includes = readIncludes(root);
        List<BuildModule> modules = new ArrayList<>();
        if (includes.isEmpty()) {
            modules.add(toModule(root, null));
        } else {
            for (String include : includes) {
                File moduleRoot = new File(root, include.replace(':', File.separatorChar));
                modules.add(toModule(moduleRoot, root.getName()));
            }
        }
        return Optional.of(new BuildProject(system, root, modules, "gradle-settings", 0.9));
    }

    private List<String> readIncludes(File root) {
        String settings = readFirstExisting(root, "settings.gradle", "settings.gradle.kts");
        if (settings.isBlank()) return List.of();
        List<String> includes = new ArrayList<>();
        for (String line : settings.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("include")) continue;
            Matcher matcher = INCLUDE_QUOTED.matcher(trimmed);
            while (matcher.find()) includes.add(matcher.group(1));
        }
        return includes;
    }

    private BuildModule toModule(File root, String parentName) {
        Set<String> plugins = new LinkedHashSet<>();
        String build = readFirstExisting(root, "build.gradle", "build.gradle.kts");
        Matcher groovyMatcher = GROOVY_PLUGIN.matcher(build);
        while (groovyMatcher.find()) plugins.add(groovyMatcher.group(1));
        Matcher kotlinMatcher = KOTLIN_PLUGIN.matcher(build);
        while (kotlinMatcher.find()) plugins.add(kotlinMatcher.group(1));
        if (build.contains(JAVA_LIBRARY)) plugins.add(JAVA_LIBRARY);

        // Kotlin DSL bare plugin shorthand: just "java" on its own line
        for (String line : build.lines().toList()) {
            String t = line.trim();
            if ("java".equals(t)) plugins.add("java");
        }

        String packaging = classifyPackaging(plugins);
        File sourceRoot = new File(root, "src/main/java");
        File resourceRoot = new File(root, "src/main/resources");
        return new BuildModule(
                root.getName(),
                root,
                parentName,
                packaging,
                List.copyOf(plugins),
                sourceRoot.isDirectory() ? List.of(sourceRoot) : List.of(),
                resourceRoot.isDirectory() ? List.of(resourceRoot) : List.of(),
                "gradle-build-script");
    }

    private String classifyPackaging(Set<String> plugins) {
        if (plugins.contains("war")) return "war";
        if (plugins.contains("org.springframework.boot")) return "boot-jar";
        if (plugins.contains("java") || plugins.contains(JAVA_LIBRARY)) return "jar";
        return "unknown";
    }

    private String readFirstExisting(File root, String first, String second) {
        File firstFile = new File(root, first);
        File secondFile = new File(root, second);
        try {
            if (firstFile.isFile()) return Files.readString(firstFile.toPath());
            if (secondFile.isFile()) return Files.readString(secondFile.toPath());
        } catch (Exception ignored) {
        }
        return "";
    }
}
