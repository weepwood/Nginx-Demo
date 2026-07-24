package com.weepwood.nginxdemo.service;

import java.io.InputStream;
import java.net.http.HttpHeaders;

public record RemoteImageResource(int statusCode, InputStream body, HttpHeaders headers, String filename) {}
