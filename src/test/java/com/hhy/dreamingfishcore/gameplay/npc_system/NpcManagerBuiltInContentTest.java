package com.hhy.dreamingfishcore.gameplay.npc_system;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcManagerBuiltInContentTest {
    @Test
    void bundledOpeningProfilesContainOnlyRetainedNpcs() {
        Map<Integer, NpcData> profiles = BuiltInNpcProfileCatalog.loadProfiles();

        assertEquals(6, profiles.size());
        assertEquals("医疗工作人员", profiles.get(106).getNpcName());
        assertEquals("白芷", profiles.get(101).getNpcName());
        assertEquals("梦屿中央医院感染医学科住院医师，现在在梦屿与外缘带地区的阿拜多斯学校进行医疗志愿。\n"
                        + "随着你们逐渐的认识，你对她的了解会变多",
                profiles.get(101).getNpcIntroduction());
        assertEquals("周岑", profiles.get(105).getNpcName());
        assertEquals("逐光会筹备处负责人", profiles.get(105).getNpcProfession());
        assertEquals("江晚", profiles.get(102).getNpcName());
        assertTrue(profiles.get(102).getNpcIntroduction().isBlank());
        assertTrue(profiles.get(102).getNpcProfession().isBlank());
        assertTrue(profiles.get(102).getDialogues().isEmpty());
        assertMinimalShell(profiles.get(102), "江晚", 2,
                "dreamingfishcore:textures/entity/npc/jiangwan.png");
        assertMinimalShell(profiles.get(103), "梁朔", 1,
                "dreamingfishcore:textures/entity/npc/liangshuo.png");
        assertMinimalShell(profiles.get(104), "尉迟南", 1,
                "dreamingfishcore:textures/entity/npc/weichinan.png");
        assertEquals("女", profiles.get(101).getNpcGender());
        assertEquals("dreamingfishcore:textures/entity/npc/baizhi.png",
                profiles.get(101).getAppearance().getSkin());
        assertEquals("slim", profiles.get(101).getAppearance().getModel());
        assertTrue(profiles.values().stream()
                .filter(profile -> !Set.of(
                        StoryNpcContentPolicy.JIANGWAN_ID,
                        StoryNpcContentPolicy.LIANGSHUO_ID,
                        StoryNpcContentPolicy.WEICHINAN_ID)
                        .contains(profile.getNpcId()))
                .allMatch(profile -> profile.getDialogues().size() >= 2));
    }

    private static void assertMinimalShell(
            NpcData profile, String name, int storyStageId, String skin) {
        assertNotNull(profile);
        assertEquals(name, profile.getNpcName());
        assertTrue(profile.getNpcIntroduction().isBlank());
        assertTrue(profile.getNpcGender().isBlank());
        assertTrue(profile.getNpcProfession().isBlank());
        assertEquals(storyStageId, profile.getStoryStageId());
        assertTrue(profile.getDialogues().isEmpty());
        assertEquals(skin, profile.getAppearance().getSkin());
        assertEquals("slim", profile.getAppearance().getModel());
        assertTrue(profile.getAppearance().isShowName());
    }

    @Test
    void shellNpcsDoNotReopenRetiredMessagesOrGuidance() {
        for (int npcId : new int[] {
                StoryNpcContentPolicy.JIANGWAN_ID,
                StoryNpcContentPolicy.LIANGSHUO_ID,
                StoryNpcContentPolicy.WEICHINAN_ID}) {
            assertTrue(StoryNpcContentPolicy.isRetained(npcId));
            assertFalse(StoryNpcContentPolicy.isRetainedMessage(
                    npcId, "dreamingfishcore:legacy/retired"));
            assertFalse(StoryNpcContentPolicy.isRetainedGuidanceSource(npcId));
        }
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

    @Test
    void addedNpcSkinsAreValid64By64TransparentJavaSkins() throws Exception {
        assertSkinResource("jiangwan.png");
        assertSkinResource("liangshuo.png");
        assertSkinResource("weichinan.png");
    }

    private void assertSkinResource(String fileName) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/assets/dreamingfishcore/textures/entity/npc/" + fileName)) {
            assertNotNull(stream);
            BufferedImage skin = ImageIO.read(stream);
            assertNotNull(skin);
            assertEquals(64, skin.getWidth());
            assertEquals(64, skin.getHeight());
            assertTrue(skin.getColorModel().hasAlpha());
        }
    }
}
