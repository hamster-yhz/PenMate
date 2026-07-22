package com.penmate.backend.infrastructure.image;

import com.penmate.backend.domain.novel.service.NovelCoverImageProcessor;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ImageIoNovelCoverImageProcessorTest {
    private final ImageIoNovelCoverImageProcessor processor = new ImageIoNovelCoverImageProcessor();

    @Test
    void crops_source_and_writes_real_webp_variants() throws Exception {
        BufferedImage source = new BufferedImage(900, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = source.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 450, 900);
        graphics.setColor(Color.BLUE);
        graphics.fillRect(450, 0, 450, 900);
        graphics.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(source, "png", png);

        NovelCoverImageProcessor.GeneratedImages result = processor.cropToWebp(png.toByteArray(),
                new NovelCoverImageProcessor.Crop(1d / 6d, 0d, 2d / 3d, 1d));

        assertThat(result.display()).startsWith((byte) 'R', (byte) 'I', (byte) 'F', (byte) 'F');
        assertThat(result.thumbnail()).startsWith((byte) 'R', (byte) 'I', (byte) 'F', (byte) 'F');
        BufferedImage display = ImageIO.read(new ByteArrayInputStream(result.display()));
        BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(result.thumbnail()));
        assertThat(display.getWidth()).isEqualTo(800);
        assertThat(display.getHeight()).isEqualTo(1200);
        assertThat(thumbnail.getWidth()).isEqualTo(240);
        assertThat(thumbnail.getHeight()).isEqualTo(360);
    }
}
