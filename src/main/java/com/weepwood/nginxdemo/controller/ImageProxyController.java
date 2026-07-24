package com.weepwood.nginxdemo.controller;

import com.weepwood.nginxdemo.service.ImageEntry;
import com.weepwood.nginxdemo.service.ProtectedNginxClient;
import com.weepwood.nginxdemo.service.RemoteImageResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/images")
public class ImageProxyController {

    private final ProtectedNginxClient nginxClient;

    public ImageProxyController(ProtectedNginxClient nginxClient) {
        this.nginxClient = nginxClient;
    }

    @GetMapping
    public List<ImageEntry> browse(@RequestParam(defaultValue = "") String path) {
        return nginxClient.browse(path);
    }

    @GetMapping("/content")
    public ResponseEntity<InputStreamResource> content(
            @RequestParam String path,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
        RemoteImageResource resource = nginxClient.open(path, range);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(resolveContentType(resource));
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(resource.filename(), StandardCharsets.UTF_8).build());
        headers.setCacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate());
        headers.set("X-Content-Type-Options", "nosniff");
        copyHeader(resource, headers, HttpHeaders.ETAG);
        copyHeader(resource, headers, HttpHeaders.LAST_MODIFIED);
        copyHeader(resource, headers, HttpHeaders.ACCEPT_RANGES);
        copyHeader(resource, headers, HttpHeaders.CONTENT_RANGE);
        resource.headers().firstValueAsLong(HttpHeaders.CONTENT_LENGTH).ifPresent(headers::setContentLength);
        return ResponseEntity.status(resource.statusCode()).headers(headers)
                .body(new InputStreamResource(resource.body()));
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        boolean available = nginxClient.checkHealth();
        return Map.of("status", available ? "UP" : "DOWN", "upstreamAvailable", available);
    }

    private MediaType resolveContentType(RemoteImageResource resource) {
        String upstream = resource.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(null);
        if (upstream != null) {
            try {
                return MediaType.parseMediaType(upstream);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return MediaTypeFactory.getMediaType(resource.filename()).orElse(MediaType.APPLICATION_OCTET_STREAM);
    }

    private void copyHeader(RemoteImageResource resource, HttpHeaders target, String name) {
        resource.headers().firstValue(name).ifPresent(value -> target.set(name, value));
    }
}
