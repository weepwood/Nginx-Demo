package com.weepwood.nginxdemo.service;

public record ImageEntry(String name, String path, boolean directory, long size, String modifiedAt) {}
