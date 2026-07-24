package com.weepwood.nginxdemo.service;

public class UpstreamImageException extends RuntimeException {

    private final int upstreamStatus;

    public UpstreamImageException(String message, int upstreamStatus) {
        super(message);
        this.upstreamStatus = upstreamStatus;
    }

    public UpstreamImageException(String message, int upstreamStatus, Throwable cause) {
        super(message, cause);
        this.upstreamStatus = upstreamStatus;
    }

    public int getUpstreamStatus() {
        return upstreamStatus;
    }
}
