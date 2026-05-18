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

        List<String> existingLines = new ArrayList<>(Files.readAllLines(configFile.toPath()));

        Set<String> existingKeys = extractKeys(existingLines);
        List<ConfigBlock> defaultBlocks = parseBlocks(defaultLines);

        Set<String> missingPaths = defaultBlocks.stream()
                .map(ConfigBlock::keyPath)
                .filter(p -> !existingKeys.contains(p))
                .collect(Collectors.toSet());

        List<Insertion> insertions = new ArrayList<>();
        for (int i = 0; i < defaultBlocks.size(); i++) {
            ConfigBlock block = defaultBlocks.get(i);
            if (!existingKeys.contains(block.keyPath)) {
                if (!hasMissingAncestor(block.keyPath, missingPaths)) {
                    List<String> sectionLines = extractSection(defaultBlocks, block.keyPath);
                    int pos = findInsertPosition(existingLines, defaultBlocks, i, existingKeys);
                    insertions.add(new Insertion(pos, sectionLines));
                }
            }
        }

        if (!insertions.isEmpty()) {
            insertions.sort((a, b) -> Integer.compare(a.position, b.position));
            List<String> merged = new ArrayList<>(existingLines);
            int offset = 0;
            for (Insertion ins : insertions) {
                int pos = Math.min(ins.position + offset, merged.size());
                merged.addAll(pos, ins.lines);
                offset += ins.lines.size();
            }
            Files.write(configFile.toPath(), merged);
        }

        plugin.reloadConfig();
    }

    private static boolean hasMissingAncestor(String keyPath, Set<String> missingPaths) {
        while (keyPath.contains(".")) {
            keyPath = keyPath.substring(0, keyPath.lastIndexOf('.'));
            if (missingPaths.contains(keyPath)) return true;
        }
        return false;
    }

    private static List<String> extractSection(List<ConfigBlock> allBlocks, String sectionRootPath) {
        int startIndex = -1;
        for (int i = 0; i < allBlocks.size(); i++) {
            if (allBlocks.get(i).keyPath.equals(sectionRootPath)) {
                startIndex = i;
                break;
            }
        }
        if (startIndex < 0) return List.of();

        List<String> result = new ArrayList<>();
        int rootLevel = allBlocks.get(startIndex).level;

        for (int i = startIndex; i < allBlocks.size(); i++) {
            ConfigBlock block = allBlocks.get(i);
            if (i > startIndex && block.level <= rootLevel) break;
            result.addAll(block.lines());
        }

        return result;
    }

    private static boolean sameParent(String keyPathA, String keyPathB) {
        if (!keyPathA.contains(".") && !keyPathB.contains(".")) return true;
        if (keyPathA.contains(".") && keyPathB.contains(".")) {
            return keyPathA.substring(0, keyPathA.lastIndexOf('.'))
                    .equals(keyPathB.substring(0, keyPathB.lastIndexOf('.')));
        }
        return false;
    }

    private static int findInsertPosition(
            List<String> existingLines,
            List<ConfigBlock> allDefaultBlocks,
            int blockIndex,
            Set<String> existingKeys
    ) {
        ConfigBlock block = allDefaultBlocks.get(blockIndex);
        int level = block.level;

        // Scan backward for the previous existing sibling at the same level
        for (int i = blockIndex - 1; i >= 0; i--) {
            ConfigBlock prev = allDefaultBlocks.get(i);
            if (prev.level == level && sameParent(prev.keyPath, block.keyPath)) {
                if (existingKeys.contains(prev.keyPath)) {
                    int end = findExistingBlockEnd(existingLines, prev.keyPath);
                    return end >= 0 ? end : existingLines.size();
                }
            }
        }

        // Scan forward for the next existing sibling at the same level
        for (int i = blockIndex + 1; i < allDefaultBlocks.size(); i++) {
            ConfigBlock next = allDefaultBlocks.get(i);
            if (next.level == level && sameParent(next.keyPath, block.keyPath)) {
                if (existingKeys.contains(next.keyPath)) {
                    int start = findExistingBlockStart(existingLines, next.keyPath);
                    return start >= 0 ? start : existingLines.size();
                }
            }
        }

        // No siblings exist at this level
        if (level == 0) return existingLines.size();

        // Find parent in existing lines and insert after its last child
        String parentPath = block.keyPath.substring(0, block.keyPath.lastIndexOf('.'));
        int parentEnd = findExistingBlockEnd(existingLines, parentPath);
        return parentEnd >= 0 ? parentEnd : existingLines.size();
    }

    private static int findExistingBlockStart(List<String> lines, String keyPath) {
        Map<Integer, String> levelKey = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            if (trimmed.contains(":")) {
                int level = countIndent(lines.get(i)) / INDENT_STEP;
                String key = trimmed.substring(0, trimmed.indexOf(':')).trim();
                levelKey.put(level, key);

                String currentPath = buildPath(levelKey, level);
                if (currentPath.equals(keyPath)) return i;
            }
        }
        return -1;
    }

    private static int findExistingBlockEnd(List<String> lines, String keyPath) {
        int start = findExistingBlockStart(lines, keyPath);
        if (start < 0) return -1;

        String[] parts = keyPath.split("\\.");
        int keyLevel = parts.length - 1;

        for (int i = start + 1; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && trimmed.contains(":")) {
                int level = countIndent(lines.get(i)) / INDENT_STEP;
                if (level <= keyLevel) return i;
            }
        }
        return lines.size();
    }

    private static String buildPath(Map<Integer, String> levelKey, int upToLevel) {
        StringBuilder path = new StringBuilder();
        for (int i = 0; i <= upToLevel; i++) {
            String k = levelKey.get(i);
            if (k != null) {
                if (path.length() > 0) path.append(".");
                path.append(k);
            }
        }
        return path.toString();
    }

    private static int countIndent(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ' ') count++;
            else break;
        }
        return count;
    }

    private static Set<String> extractKeys(List<String> lines) {
        Set<String> keys = new HashSet<>();
        Map<Integer, String> levelKey = new HashMap<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            if (trimmed.contains(":")) {
                int level = countIndent(line) / INDENT_STEP;
                String key = trimmed.substring(0, trimmed.indexOf(':')).trim();
                levelKey.put(level, key);
                keys.add(buildPath(levelKey, level));
            }
        }
        return keys;
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
                int indent = countIndent(line);
                int level = indent / INDENT_STEP;
                String key = trimmed.substring(0, trimmed.indexOf(':')).trim();
                levelKey.put(level, key);

                blocks.add(new ConfigBlock(buildPath(levelKey, level), level, new ArrayList<>(comments), line));
                comments.clear();
            }
        }
        return blocks;
    }

    private record ConfigBlock(String keyPath, int level, List<String> comments, String dataLine) {
        public List<String> lines() {
            List<String> result = new ArrayList<>(comments);
            result.add(dataLine);
            return result;
        }
    }

    private record Insertion(int position, List<String> lines) {}
}
