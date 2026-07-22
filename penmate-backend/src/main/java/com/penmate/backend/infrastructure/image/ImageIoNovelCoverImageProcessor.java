package com.penmate.backend.infrastructure.image;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.novel.service.NovelCoverImageProcessor;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

@Component
public class ImageIoNovelCoverImageProcessor implements NovelCoverImageProcessor {
    private static final long MAX_PIXELS = 40_000_000L;

    @Override
    public ImageInfo inspect(byte[] source) {
        BufferedImage image = read(source);
        validateDimensions(image);
        return new ImageInfo(image.getWidth(), image.getHeight());
    }

    @Override
    public GeneratedImages cropToWebp(byte[] source, Crop crop) {
        BufferedImage image = read(source);
        validateDimensions(image);
        int x = (int) Math.floor(crop.x() * image.getWidth());
        int y = (int) Math.floor(crop.y() * image.getHeight());
        int width = Math.min(image.getWidth() - x, Math.max(1, (int) Math.round(crop.width() * image.getWidth())));
        int height = Math.min(image.getHeight() - y, Math.max(1, (int) Math.round(crop.height() * image.getHeight())));
        if (x < 0 || y < 0 || width <= 0 || height <= 0) {
            throw BusinessException.badRequest("Cover crop is outside the source image");
        }
        BufferedImage cropped = image.getSubimage(x, y, width, height);
        return new GeneratedImages(encodeWebp(resize(cropped, 800, 1200), 0.88f),
                encodeWebp(resize(cropped, 240, 360), 0.82f));
    }

    private BufferedImage read(byte[] source) {
        if (source == null || source.length == 0) throw BusinessException.badRequest("Cover image is empty");
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(source));
            if (image == null) throw BusinessException.badRequest("Unsupported or invalid cover image");
            return image;
        } catch (IOException exception) {
            throw BusinessException.badRequest("Unsupported or invalid cover image");
        }
    }

    private void validateDimensions(BufferedImage image) {
        long pixels = (long) image.getWidth() * image.getHeight();
        if (image.getWidth() < 2 || image.getHeight() < 3 || pixels > MAX_PIXELS) {
            throw BusinessException.badRequest("Cover image dimensions are invalid or too large");
        }
    }

    private BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private byte[] encodeWebp(BufferedImage image, float quality) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
        if (!writers.hasNext()) throw new IllegalStateException("WebP image writer is unavailable");
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                String[] compressionTypes = params.getCompressionTypes();
                if (compressionTypes != null && compressionTypes.length > 0) {
                    params.setCompressionType(compressionTypes[0]);
                }
                params.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), params);
            imageOutput.flush();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to encode cover as WebP", exception);
        } finally {
            writer.dispose();
        }
    }
}
