package com.hhy.dreamingfishcore.gameplay.npc_system;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcManagerBuiltInContentTest {
    @Test
    void bundledOpeningProfilesContainOnlyRetainedNpcs() {
        Map<Integer, NpcData> profiles = BuiltInNpcProfileCatalog.loadProfiles();

        assertEquals(2, profiles.size());
        assertEquals("白芷", profiles.get(101).getNpcName());
        assertEquals("梦屿中央医院感染医学科住院医师，现在在梦屿与外缘带地区的阿拜多斯学校进行医疗志愿。\n"
                        + "随着你们逐渐的认识，你对她的了解会变多",
                profiles.get(101).getNpcIntroduction());
        assertEquals("周岑", profiles.get(105).getNpcName());
        assertEquals("逐光会筹备处负责人", profiles.get(105).getNpcProfession());
        assertEquals("女", profiles.get(101).getNpcGender());
        assertEquals("dreamingfishcore:textures/entity/npc/baizhi.png",
                profiles.get(101).getAppearance().getSkin());
        assertEquals("slim", profiles.get(101).getAppearance().getModel());
        assertTrue(profiles.values().stream()
                .allMatch(profile -> profile.getDialogues().size() >= 2));
    }

    @Test
    void baizhiSkinIsAValidTransparentJavaSkin() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/assets/dreamingfishcore/textures/entity/npc/baizhi.png")) {
            assertNotNull(stream);
            BufferedImage skin = ImageIO.read(stream);
            assertNotNull(skin);
            assertEquals(64, skin.getWidth());
            assertEquals(64, skin.getHeight());
            assertTrue(skin.getColorModel().hasAlpha());
            assertEquals(0, skin.getRGB(0, 0) >>> 24);
            assertEquals(255, skin.getRGB(8, 8) >>> 24);
        }
    }
}
