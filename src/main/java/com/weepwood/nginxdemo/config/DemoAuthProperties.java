package com.weepwood.nginxdemo.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.auth")
public class DemoAuthProperties {

    @NotBlank
    private String username = "demo";

    @NotBlank
    private String password = "demo-pass";

    @NotBlank
    private String token = "demo-token-change-me";

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
