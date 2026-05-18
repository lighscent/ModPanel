package com.xl1te.modpanel.config;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class PluginConfig {

    private static final int INDENT_STEP = 2;

    public static void updateConfig(JavaPlugin plugin) throws IOException {
        File configFile = new File(plugin.getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
            plugin.reloadConfig();
            return;
        }

        List<String> defaultLines;
        try (InputStream is = plugin.getResource("config.yml")) {
            if (is == null) {
                plugin.reloadConfig();
                return;
            }
            defaultLines = new BufferedReader(new InputStreamReader(is))
                    .lines().collect(Collectors.toList());
        }

        List<String> lines = new ArrayList<>(Files.readAllLines(configFile.toPath()));
        List<ConfigBlock> defaultBlocks = parseBlocks(defaultLines);

        for (int i = 0; i < defaultBlocks.size(); i++) {
            ConfigBlock block = defaultBlocks.get(i);
            if (!keyExists(lines, block.keyPath)) {
                if (!hasMissingAncestor(block.keyPath, defaultBlocks, i)) {
                    List<String> section = extractSection(defaultBlocks, i);
                    int pos = findInsertPosition(lines, defaultBlocks, i);
                    lines.add(Math.min(pos, lines.size()), section.get(0));
                    int inserted = 1;
                    for (int j = 1; j < section.size(); j++) {
                        lines.add(Math.min(pos + inserted, lines.size()), section.get(j));
                        inserted++;
                    }
                }
            }
        }

        Files.write(configFile.toPath(), lines);
        plugin.reloadConfig();
    }

    private static boolean hasMissingAncestor(String keyPath, List<ConfigBlock> blocks, int upTo) {
        String base = keyPath;
        while (base.contains(".")) {
            base = base.substring(0, base.lastIndexOf('.'));
            for (int i = 0; i < upTo; i++) {
                if (blocks.get(i).keyPath.equals(base)) {
                    if (!keyExistsWithBlocks(blocks, i, base)) return true;
                    break;
                }
            }
        }
        return false;
    }

    private static boolean keyExistsWithBlocks(List<ConfigBlock> blocks, int end, String keyPath) {
        for (int i = 0; i < end; i++) {
            if (blocks.get(i).keyPath.equals(keyPath)) return true;
        }
        return false;
    }

    private static List<String> extractSection(List<ConfigBlock> blocks, int fromIndex) {
        List<String> result = new ArrayList<>();
        int rootLevel = blocks.get(fromIndex).level;

        for (int i = fromIndex; i < blocks.size(); i++) {
            ConfigBlock b = blocks.get(i);
            if (i > fromIndex && b.level <= rootLevel) break;
            result.add(b.dataLine());
        }
        return result;
    }

    private static boolean keyExists(List<String> lines, String keyPath) {
        return findLineIndex(lines, keyPath) >= 0;
    }

    private static int findLineIndex(List<String> lines, String keyPath) {
        Map<Integer, String> levelKey = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            if (trimmed.contains(":")) {
                int level = countIndent(lines.get(i)) / INDENT_STEP;
                String key = trimmed.substring(0, trimmed.indexOf(':')).trim();
                levelKey.put(level, key);
                if (buildPath(levelKey, level).equals(keyPath)) return i;
            }
        }
        return -1;
    }

    private static int findBlockEnd(List<String> lines, String keyPath) {
        int start = findLineIndex(lines, keyPath);
        if (start < 0) return -1;
        int targetLevel = keyPath.split("\\.").length - 1;
        for (int i = start + 1; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            if (t.contains(":")) {
                int level = countIndent(lines.get(i)) / INDENT_STEP;
                if (level <= targetLevel) return i;
            }
        }
        return lines.size();
    }

    private static int findInsertPosition(List<String> lines, List<ConfigBlock> allBlocks, int blockIndex) {
        ConfigBlock block = allBlocks.get(blockIndex);
        int level = block.level;
        String keyPath = block.keyPath;

        // Find previous existing sibling in default order
        for (int i = blockIndex - 1; i >= 0; i--) {
            ConfigBlock prev = allBlocks.get(i);
            if (prev.level == level && sameParent(prev.keyPath, keyPath)) {
                int lineIdx = findLineIndex(lines, prev.keyPath);
                if (lineIdx >= 0) {
                    int end = findBlockEnd(lines, prev.keyPath);
                    return end >= 0 ? end : lines.size();
                }
            }
        }

        // Find next existing sibling in default order
        for (int i = blockIndex + 1; i < allBlocks.size(); i++) {
            ConfigBlock next = allBlocks.get(i);
            if (next.level == level && sameParent(next.keyPath, keyPath)) {
                int lineIdx = findLineIndex(lines, next.keyPath);
                if (lineIdx >= 0) return lineIdx;
            }
        }

        if (level == 0) return lines.size();

        String parentPath = keyPath.substring(0, keyPath.lastIndexOf('.'));
        int parentEnd = findBlockEnd(lines, parentPath);
        return parentEnd >= 0 ? parentEnd : lines.size();
    }

    private static boolean sameParent(String a, String b) {
        if (!a.contains(".") && !b.contains(".")) return true;
        if (!a.contains(".") || !b.contains(".")) return false;
        return a.substring(0, a.lastIndexOf('.')).equals(b.substring(0, b.lastIndexOf('.')));
    }

    private static String buildPath(Map<Integer, String> levelKey, int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= level; i++) {
            String k = levelKey.get(i);
            if (k != null) {
                if (sb.length() > 0) sb.append(".");
                sb.append(k);
            }
        }
        return sb.toString();
    }

    private static int countIndent(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') count++;
            else break;
        }
        return count;
    }

    private static List<ConfigBlock> parseBlocks(List<String> lines) {
        List<ConfigBlock> blocks = new ArrayList<>();
        List<String> comments = new ArrayList<>();
        Map<Integer, String> levelKey = new HashMap<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                comments.add(line);
                continue;
            }
            if (trimmed.contains(":")) {
                int level = countIndent(line) / INDENT_STEP;
                String key = trimmed.substring(0, trimmed.indexOf(':')).trim();
                levelKey.put(level, key);
                blocks.add(new ConfigBlock(buildPath(levelKey, level), level, new ArrayList<>(comments), line));
                comments.clear();
            }
        }
        return blocks;
    }

    private record ConfigBlock(String keyPath, int level, List<String> comments, String dataLine) {}
}