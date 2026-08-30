package com.hhy.dreamingfishcore.gameplay.story_system.runtime;

import net.minecraft.resources.ResourceLocation;

/** 内容包中一次物品奖励的稳定 ID 和数量。 */
public final class StoryFlowItemGrant {
    private String itemId = "";
    private int count = 1;

    public StoryFlowItemGrant() {
    }

    public StoryFlowItemGrant(String itemId, int count) {
        this.itemId = itemId;
        this.count = count;
    }

    public String getItemId() {
        return itemId == null ? "" : itemId.trim();
    }

    public int getCount() {
        return count;
    }

    void validate(String flowId, String nodeId) {
        if (ResourceLocation.tryParse(getItemId()) == null
                || count <= 0 || count > 64 * 1024) {
            throw new IllegalStateException(
                    "故事物品奖励非法：" + flowId + "/" + nodeId + "/" + getItemId());
        }
    }
}
