package com.weepwood.nginxdemo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weepwood.nginxdemo.config.ImageProxyProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ProtectedNginxClient {

    private static final Pattern RANGE_PATTERN = Pattern.compile("bytes=(?:\\d+-\\d*|-\\d+)");

    private final ImageProxyProperties properties;
    private final ImagePathValidator pathValidator;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI baseUri;
    private final String basicAuthorization;

    public ProtectedNginxClient(ImageProxyProperties properties, ImagePathValidator pathValidator, ObjectMapper objectMapper) {
        this.properties = properties;
        this.pathValidator = pathValidator;
        this.objectMapper = objectMapper;
        this.baseUri = normalizeBaseUri(properties.getBaseUrl());
        this.basicAuthorization = createBasicAuthorization(properties.getUsername(), properties.getPassword());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(validDuration(properties.getConnectTimeout(), Duration.ofSeconds(5)))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public List<ImageEntry> browse(String rawPath) {
        String normalizedPath = pathValidator.normalizeDirectory(rawPath);
        URI uri = resolve(normalizedPath, true);
        HttpRequest request = requestBuilder(uri).header(HttpHeaders.ACCEPT, "application/json").GET().build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            ensureSuccess(response.statusCode(), false);
            byte[] body = response.body();
            if (body.length > properties.getMaxDirectoryBytes()) {
                throw new UpstreamImageException("目录索引响应过大", 502);
            }
            return parseDirectory(normalizedPath, body);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new UpstreamImageException("读取 Nginx 目录时被中断", 502, exception);
        } catch (IOException exception) {
            throw new UpstreamImageException("无法连接 Nginx 图片服务器", 502, exception);
        }
    }

    public RemoteImageResource open(String rawPath, String rangeHeader) {
        String normalizedPath = pathValidator.normalizeFile(rawPath);
        URI uri = resolve(normalizedPath, false);
        HttpRequest.Builder builder = requestBuilder(uri)
                .header(HttpHeaders.ACCEPT, "image/*,application/octet-stream;q=0.8")
                .GET();
        if (rangeHeader != null && !rangeHeader.isBlank()) {
            if (!RANGE_PATTERN.matcher(rangeHeader.trim()).matches()) {
                throw new InvalidImagePathException("Range 请求头格式不正确");
            }
            builder.header(HttpHeaders.RANGE, rangeHeader.trim());
        }

        try {
            HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200 && response.statusCode() != 206) {
                response.body().close();
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    throw new UpstreamImageException("Nginx 文件服务返回不支持的成功状态码 " + response.statusCode(), response.statusCode());
                }
                ensureSuccess(response.statusCode(), true);
            }
            return new RemoteImageResource(response.statusCode(), response.body(), response.headers(), filename(normalizedPath));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new UpstreamImageException("读取 Nginx 文件时被中断", 502, exception);
        } catch (IOException exception) {
            throw new UpstreamImageException("无法读取 Nginx 文件", 502, exception);
        }
    }

    public boolean checkHealth() {
        try {
            browse("");
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private List<ImageEntry> parseDirectory(String currentPath, byte[] body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        if (!root.isArray()) throw new UpstreamImageException("Nginx 未返回 JSON 目录索引", 502);
        List<ImageEntry> result = new ArrayList<>();
        for (JsonNode node : root) {
            if (result.size() >= properties.getMaxDirectoryEntries()) break;
            String name = node.path("name").asText("").trim();
            String type = node.path("type").asText("").trim().toLowerCase(Locale.ROOT);
            if (!isSafeChildName(name)) continue;
            boolean directory = "directory".equals(type) || name.endsWith("/");
            String cleanName = stripTrailingSlash(name);
            String childPath = currentPath + cleanName + (directory ? "/" : "");
            result.add(new ImageEntry(cleanName, childPath, directory,
                    directory ? 0L : Math.max(0L, node.path("size").asLong(0L)),
                    node.path("mtime").asText(null)));
        }
        return List.copyOf(result);
    }

    private HttpRequest.Builder requestBuilder(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(validDuration(properties.getReadTimeout(), Duration.ofSeconds(60)))
                .header(HttpHeaders.AUTHORIZATION, basicAuthorization)
                .header(HttpHeaders.USER_AGENT, "Nginx-Demo/1.0");
    }

    private URI resolve(String normalizedPath, boolean directory) {
        String encodedPath = encodePath(normalizedPath);
        if (encodedPath.isEmpty()) return baseUri;
        if (directory && !encodedPath.endsWith("/")) encodedPath += "/";
        URI resolved = baseUri.resolve(encodedPath);
        if (!sameOrigin(baseUri, resolved) || !resolved.getPath().startsWith(baseUri.getPath())) {
            throw new InvalidImagePathException("路径超出允许的图片根目录");
        }
        return resolved;
    }

    private String encodePath(String path) {
        if (path == null || path.isEmpty()) return "";
        StringBuilder encoded = new StringBuilder();
        String[] segments = path.split("/", -1);
        for (int index = 0; index < segments.length; index++) {
            String segment = segments[index];
            if (!segment.isEmpty()) {
                try {
                    encoded.append(new URI(null, null, segment, null).getRawPath());
                } catch (URISyntaxException exception) {
                    throw new InvalidImagePathException("路径编码失败");
                }
            }
            if (index < segments.length - 1) encoded.append('/');
        }
        return encoded.toString();
    }

    private URI normalizeBaseUri(URI raw) {
        if (raw == null || raw.getScheme() == null || raw.getHost() == null) {
            throw new IllegalStateException("image.base-url 必须是完整的 HTTP/HTTPS URL");
        }
        String scheme = raw.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalStateException("image.base-url 仅允许 HTTP/HTTPS");
        }
        if (raw.getUserInfo() != null || raw.getQuery() != null || raw.getFragment() != null) {
            throw new IllegalStateException("image.base-url 不能包含凭据、查询参数或片段");
        }
        try {
            String path = raw.getPath() == null || raw.getPath().isBlank() ? "/" : raw.getPath();
            if (!path.endsWith("/")) path += "/";
            return new URI(scheme, null, raw.getHost(), raw.getPort(), path, null, null);
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("image.base-url 格式不正确", exception);
        }
    }

    private String createBasicAuthorization(String username, String password) {
        String value = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private void ensureSuccess(int statusCode, boolean file) {
        if (statusCode >= 200 && statusCode < 300) return;
        String target = file ? "文件" : "目录";
        String message = switch (statusCode) {
            case 401 -> "Nginx " + target + "认证失败，请检查 IMAGE_USERNAME / IMAGE_PASSWORD";
            case 403 -> "Nginx 拒绝访问" + target;
            case 404 -> "Nginx " + target + "不存在";
            default -> "Nginx " + target + "服务返回状态码 " + statusCode;
        };
        throw new UpstreamImageException(message, statusCode);
    }

    private boolean isSafeChildName(String value) {
        if (value == null || value.isBlank()) return false;
        String clean = stripTrailingSlash(value);
        return !clean.isBlank() && !".".equals(clean) && !"..".equals(clean)
                && clean.indexOf('/') < 0 && clean.indexOf('\\') < 0 && clean.indexOf(':') < 0
                && clean.chars().noneMatch(character -> character < 32 || character == 127);
    }

    private String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private String filename(String path) {
        int separator = path.lastIndexOf('/');
        return separator < 0 ? path : path.substring(separator + 1);
    }

    private boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443;
    }

    private Duration validDuration(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
