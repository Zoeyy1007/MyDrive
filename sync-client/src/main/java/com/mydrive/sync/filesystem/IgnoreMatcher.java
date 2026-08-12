package com.mydrive.sync.filesystem;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class IgnoreMatcher {
    private static final List<String> DEFAULTS = List.of(
            ".mydrive", ".mydrive/**", "*.mydrive.tmp");

    private final List<Rule> rules;

    public IgnoreMatcher(List<String> configuredPatterns) {
        List<String> patterns = new ArrayList<>(DEFAULTS);
        if (configuredPatterns != null) patterns.addAll(configuredPatterns);
        this.rules = patterns.stream()
                .filter(pattern -> pattern != null && !pattern.isBlank())
                .map(String::trim)
                .map(Rule::new)
                .toList();
    }

    public boolean isIgnored(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return false;
        String portable = relativePath.replace('\\', '/');
        String filename = portable.substring(portable.lastIndexOf('/') + 1);
        return rules.stream().anyMatch(rule -> rule.matches(portable, filename));
    }

    private static final class Rule {
        private final boolean pathRule;
        private final Pattern matcher;

        private Rule(String pattern) {
            this.pathRule = pattern.indexOf('/') >= 0;
            this.matcher = Pattern.compile(globRegex(pattern));
        }

        private boolean matches(String path, String filename) {
            return matcher.matcher(path).matches()
                    || (!pathRule && matcher.matcher(filename).matches());
        }

        private static String globRegex(String glob) {
            StringBuilder regex = new StringBuilder("^");
            for (int i = 0; i < glob.length(); i++) {
                char character = glob.charAt(i);
                if (character == '*') {
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        regex.append(".*");
                        i++;
                    } else {
                        regex.append("[^/]*");
                    }
                } else if (character == '?') {
                    regex.append("[^/]");
                } else {
                    if ("\\.^$|()[]{}+".indexOf(character) >= 0) regex.append('\\');
                    regex.append(character);
                }
            }
            return regex.append('$').toString();
        }
    }
}
