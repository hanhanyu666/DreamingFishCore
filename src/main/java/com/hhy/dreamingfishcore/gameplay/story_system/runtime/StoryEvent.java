package com.hhy.dreamingfishcore.gameplay.story_system.runtime;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

/**
 * 一个短生命周期的、不可变的故事事实。
 *
 * <p>事件不保存到配置，也不允许客户端构造后直接提交；入口方法由服务端模块调用。
 * {@code player} 仅用于当前 tick 执行效果，不会被序列化。</p>
 */
public record StoryEvent(
        StoryEventType type,
        UUID playerId,
        String playerName,
        ServerPlayer player,
        String subjectId,
        String secondaryId,
        String locationId,
        Map<String, String> attributes) {

    public StoryEvent {
        if (type == null) {
            throw new IllegalArgumentException("故事事件类型不能为空");
        }
        subjectId = normalize(subjectId);
        secondaryId = normalize(secondaryId);
        locationId = normalize(locationId);
        playerName = playerName == null ? "" : playerName;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static StoryEvent authenticated(ServerPlayer player) {
        return forPlayer(StoryEventType.PLAYER_AUTHENTICATED, player, "", "", "", Map.of());
    }

    public static StoryEvent noticeRead(
            ServerPlayer player, String noticeKey, String noticeTitle) {
        return forPlayer(
                StoryEventType.NOTICE_READ,
                player,
                noticeKey,
                "",
                "",
                Map.of("noticeTitle", noticeTitle == null ? "" : noticeTitle));
    }

    public static StoryEvent locationEntered(
            ServerPlayer player, String locationId, String locationName) {
        return forPlayer(
                StoryEventType.LOCATION_ENTERED,
                player,
                "",
                "",
                locationId,
                Map.of("locationName", locationName == null ? "" : locationName));
    }

    public static StoryEvent npcInteraction(ServerPlayer player, int npcId) {
        return forPlayer(
                StoryEventType.NPC_INTERACTION,
                player,
                Integer.toString(npcId),
                "",
                "",
                Map.of());
    }

    public static StoryEvent npcReply(
            ServerPlayer player, String messageDefinitionId, String replyId) {
        return forPlayer(
                StoryEventType.NPC_REPLY,
                player,
                messageDefinitionId,
                replyId,
                "",
                Map.of());
    }

    private static StoryEvent forPlayer(
            StoryEventType type,
            ServerPlayer player,
            String subjectId,
            String secondaryId,
            String locationId,
            Map<String, String> attributes) {
        UUID playerId = player == null ? null : player.getUUID();
        String playerName = player == null ? "" : player.getScoreboardName();
        return new StoryEvent(
                type,
                playerId,
                playerName,
                player,
                subjectId,
                secondaryId,
                locationId,
                attributes);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
