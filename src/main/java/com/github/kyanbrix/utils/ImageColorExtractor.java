package com.github.kyanbrix.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URL;

public class ImageColorExtractor {


    private static final Logger log = LoggerFactory.getLogger(ImageColorExtractor.class);

    public static Color getColor(String imageUrl) {


        try {
            URL url = URI.create(imageUrl).toURL();

            BufferedImage image = ImageIO.read(url);

            long red = 0, green = 0, blue = 0;
            int vibrantPixelCount = 0;

            for (int x = 0; x < image.getWidth(); x += 2) {
                for (int y = 0; y < image.getHeight(); y += 2) {
                    int rgb = image.getRGB(x, y);

                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;

                    float[] hsb = Color.RGBtoHSB(r, g, b, null);
                    float saturation = hsb[1];
                    float brightness = hsb[2];

                    if (brightness > 0.15f && brightness < 0.95f && saturation > 0.20f) {
                        red += r;
                        green += g;
                        blue += b;
                        vibrantPixelCount++;
                    }
                }
            }

            if (vibrantPixelCount == 0) return Color.RED;

            return new Color(
                    (int) (red / vibrantPixelCount),
                    (int) (green / vibrantPixelCount),
                    (int) (blue / vibrantPixelCount)
            );



        }catch(Exception e) {
            log.error(e.getMessage());
            return Color.GREEN;
        }



    }

}
