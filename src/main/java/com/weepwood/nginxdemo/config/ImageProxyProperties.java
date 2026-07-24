package com.weepwood.nginxdemo.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "image")
public class ImageProxyProperties {

    @NotNull
    private URI baseUrl = URI.create("http://localhost:8081/images/");

    @NotBlank
    private String username = "mrr-image";

    @NotBlank
    private String password = "change-me";

    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(60);
    private int maxDirectoryBytes = 4 * 1024 * 1024;
    private int maxDirectoryEntries = 500;

    public URI getBaseUrl() { return baseUrl; }
    public void setBaseUrl(URI baseUrl) { this.baseUrl = baseUrl; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public int getMaxDirectoryBytes() { return maxDirectoryBytes; }
    public void setMaxDirectoryBytes(int maxDirectoryBytes) { this.maxDirectoryBytes = maxDirectoryBytes; }
    public int getMaxDirectoryEntries() { return maxDirectoryEntries; }
    public void setMaxDirectoryEntries(int maxDirectoryEntries) { this.maxDirectoryEntries = maxDirectoryEntries; }
}
