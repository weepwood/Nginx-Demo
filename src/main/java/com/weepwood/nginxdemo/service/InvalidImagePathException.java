package com.weepwood.nginxdemo.service;

public class InvalidImagePathException extends RuntimeException {
    public InvalidImagePathException(String message) {
        super(message);
    }
}
