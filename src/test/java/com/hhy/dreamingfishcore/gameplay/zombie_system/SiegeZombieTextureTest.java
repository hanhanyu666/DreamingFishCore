package com.hhy.dreamingfishcore.gameplay.zombie_system;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiegeZombieTextureTest {
    private static final String TEXTURE_ROOT =
            "/assets/dreamingfishcore/textures/entity/siege_zombie/";
    private static final List<String> SKINS = List.of(
            "zombiedf1.png",
            "zombiedf2.png",
            "zombiedf3.png",
            "zombiedf4.png",
            "zombiedf5.png",
            "qingmozangbi.png",
            "left2mine_zombie.png",
            "zombie_girl.png",
            "hanhanyu_z.png",
            "qingmo_z.png",
            "wither_light_z.png",
            "jijituan_z.png");
    private static final List<String> EYE_LIGHTS = List.of(
            "eyelight_blue.png",
            "eyelight_green.png",
            "eyelight_pink.png",
            "eyelight_red2.png",
            "eyelight_white.png",
            "eyelight_yellow.png");

    @Test
    void allSuppliedEyeLightsUseTheSameVanillaUvAndDistinctColors() throws IOException {
        Set<Point> expectedPixels = Set.of(
                new Point(9, 12),
                new Point(10, 12),
                new Point(13, 12),
                new Point(14, 12));
        Set<Integer> colors = new HashSet<>();

        for (String fileName : EYE_LIGHTS) {
            BufferedImage image = readTexture(TEXTURE_ROOT + fileName);
            assertEquals(64, image.getWidth());
            assertEquals(64, image.getHeight());

            Set<Point> opaquePixels = new HashSet<>();
            Set<Integer> textureColors = new HashSet<>();
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    if ((argb >>> 24) != 0) {
                        opaquePixels.add(new Point(x, y));
                        textureColors.add(argb);
                    }
                }
            }
            assertEquals(expectedPixels, opaquePixels, fileName);
            assertEquals(1, textureColors.size(), fileName);
            colors.add(textureColors.iterator().next());
        }
        assertEquals(EYE_LIGHTS.size(), colors.size());
    }

    @Test
    void allTwelveSuppliedSkinsUseVanillaTextureDimensionsAndRemainDistinct() throws IOException {
        Set<Integer> rasterSignatures = new HashSet<>();
        for (String fileName : SKINS) {
            BufferedImage image = readTexture(TEXTURE_ROOT + fileName);
            assertEquals(64, image.getWidth(), fileName);
            assertEquals(64, image.getHeight(), fileName);
            assertTrue(countOpaquePixels(image) >= 1000, fileName);
            rasterSignatures.add(Arrays.hashCode(image.getRGB(
                    0,
                    0,
                    image.getWidth(),
                    image.getHeight(),
                    null,
                    0,
                    image.getWidth())));
        }
        assertEquals(SKINS.size(), rasterSignatures.size());
    }

    private static BufferedImage readTexture(String resource) throws IOException {
        try (InputStream input = SiegeZombieTextureTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, "missing siege zombie texture: " + resource);
            BufferedImage image = ImageIO.read(input);
            assertNotNull(image, "unreadable siege zombie texture: " + resource);
            return image;
        }
    }

    private static int countOpaquePixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    count++;
                }
            }
        }
        return count;
    }
}
