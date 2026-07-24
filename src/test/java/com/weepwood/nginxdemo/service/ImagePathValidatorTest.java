package com.weepwood.nginxdemo.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImagePathValidatorTest {

    private final ImagePathValidator validator = new ImagePathValidator();

    @Test
    void normalizesDirectoryPath() {
        assertThat(validator.normalizeDirectory("/cases/0001/")).isEqualTo("cases/0001/");
    }

    @Test
    void normalizesFilePath() {
        assertThat(validator.normalizeFile("cases/0001/sample.svg")).isEqualTo("cases/0001/sample.svg");
    }

    @Test
    void rejectsTraversal() {
        assertThatThrownBy(() -> validator.normalizeFile("../secret.txt"))
                .isInstanceOf(InvalidImagePathException.class);
    }

    @Test
    void rejectsWindowsAbsolutePath() {
        assertThatThrownBy(() -> validator.normalizeFile("C:/images/a.jpg"))
                .isInstanceOf(InvalidImagePathException.class);
    }

    @Test
    void allowsRootDirectory() {
        assertThat(validator.normalizeDirectory(null)).isEmpty();
    }
}
