package com.penmate.backend.domain.novel.service;

public interface NovelCoverImageProcessor {
    ImageInfo inspect(byte[] source);

    GeneratedImages cropToWebp(byte[] source, Crop crop);

    record ImageInfo(int width, int height) {
    }

    record Crop(double x, double y, double width, double height) {
    }

    record GeneratedImages(byte[] display, byte[] thumbnail) {
    }
}
