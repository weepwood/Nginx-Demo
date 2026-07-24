package com.weepwood.nginxdemo.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ImagePathValidator {

    public String normalizeDirectory(String rawPath) {
        String normalized = normalize(rawPath, true);
        return normalized.isEmpty() ? "" : normalized + "/";
    }

    public String normalizeFile(String rawPath) {
        String normalized = normalize(rawPath, false);
        if (normalized.isEmpty()) {
            throw new InvalidImagePathException("文件路径不能为空");
        }
        return normalized;
    }

    private String normalize(String rawPath, boolean directory) {
        String value = rawPath == null ? "" : rawPath.trim();
        if (value.indexOf('\\') >= 0 || value.indexOf('\0') >= 0 || value.indexOf('?') >= 0 || value.indexOf('#') >= 0) {
            throw new InvalidImagePathException("路径包含非法字符");
        }
        while (value.startsWith("/")) value = value.substring(1);
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);

        if (value.isEmpty()) {
            if (directory) return "";
            throw new InvalidImagePathException("文件路径不能为空");
        }

        List<String> segments = new ArrayList<>();
        for (String segment : value.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)
                    || segment.contains(":") || containsControlCharacter(segment)) {
                throw new InvalidImagePathException("路径包含非法目录段");
            }
            segments.add(segment);
        }
        return String.join("/", segments);
    }

    private boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(character -> character < 32 || character == 127);
    }
}
