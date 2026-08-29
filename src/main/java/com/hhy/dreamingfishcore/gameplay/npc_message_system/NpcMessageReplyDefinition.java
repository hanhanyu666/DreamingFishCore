package com.hhy.dreamingfishcore.gameplay.npc_message_system;

import com.hhy.dreamingfishcore.gameplay.zhuiguang_system.ZhuiguangMembershipAction;
import com.hhy.dreamingfishcore.gameplay.zhuiguang_system.ZhuiguangMembershipRequirement;

/** NPC 消息中由作者提供的有限回复选项。 */
public class NpcMessageReplyDefinition {
    private String id = "";
    private String text = "";
    private int minimumFavorability = -1000;
    private int maximumFavorability = 1000;
    private int favorabilityDelta;
    private String followUpMessageId = "";
    private ZhuiguangMembershipRequirement membershipRequirement = ZhuiguangMembershipRequirement.ANY;
    private ZhuiguangMembershipAction membershipAction = ZhuiguangMembershipAction.NONE;

    public NpcMessageReplyDefinition() {
    }

    public NpcMessageReplyDefinition(String id, String text, int favorabilityDelta, String followUpMessageId) {
        this.id = id;
        this.text = text;
        this.favorabilityDelta = favorabilityDelta;
        this.followUpMessageId = followUpMessageId;
    }

    public String getId() {
        return id == null ? "" : id;
    }

    public String getText() {
        return text == null ? "" : text;
    }

    public int getMinimumFavorability() {
        return minimumFavorability;
    }

    public int getMaximumFavorability() {
        return maximumFavorability;
    }

    public int getFavorabilityDelta() {
        return favorabilityDelta;
    }

    public String getFollowUpMessageId() {
        return followUpMessageId == null ? "" : followUpMessageId;
    }

    public ZhuiguangMembershipRequirement getMembershipRequirement() {
        return membershipRequirement == null
                ? ZhuiguangMembershipRequirement.ANY
                : membershipRequirement;
    }

    public ZhuiguangMembershipAction getMembershipAction() {
        return membershipAction == null ? ZhuiguangMembershipAction.NONE : membershipAction;
    }

    public boolean isAvailableAt(int favorability) {
        return favorability >= minimumFavorability && favorability <= maximumFavorability;
    }

    public boolean isAvailableFor(int favorability, boolean zhuiguangMember) {
        return isAvailableAt(favorability) && getMembershipRequirement().matches(zhuiguangMember);
    }

    public NpcMessageReplyDefinition requiringFavorability(int minimum, int maximum) {
        this.minimumFavorability = minimum;
        this.maximumFavorability = maximum;
        return this;
    }

    public NpcMessageReplyDefinition requiringMembership(ZhuiguangMembershipRequirement requirement) {
        this.membershipRequirement = requirement == null
                ? ZhuiguangMembershipRequirement.ANY
                : requirement;
        return this;
    }

    public NpcMessageReplyDefinition withMembershipAction(ZhuiguangMembershipAction action) {
        this.membershipAction = action == null ? ZhuiguangMembershipAction.NONE : action;
        return this;
    }
}
