package com.hhy.dreamingfishcore.client.ui.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImmersiveChatManagerTest {
    @Test
    void detectsACompleteMentionIgnoringCase() {
        assertTrue(ImmersiveChatManager.containsMention("欢迎 @HanHanYu 回来", "hanhanyu"));
    }

    @Test
    void acceptsMentionNextToPunctuation() {
        assertTrue(ImmersiveChatManager.containsMention("(@HanHanYu)，看这里", "HanHanYu"));
    }

    @Test
    void rejectsLongerPlayerNameWithSamePrefix() {
        assertFalse(ImmersiveChatManager.containsMention("你好 @HanHanYu2", "HanHanYu"));
    }

    @Test
    void rejectsPlainPlayerNameWithoutMentionMarker() {
        assertFalse(ImmersiveChatManager.containsMention("HanHanYu 看这里", "HanHanYu"));
    }
}
