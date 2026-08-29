package com.hhy.dreamingfishcore.gameplay.npc_message_system;

import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceSeed;
import com.hhy.dreamingfishcore.gameplay.zhuiguang_system.ZhuiguangMembershipRequirement;

import java.util.ArrayList;
import java.util.List;

/** 由服务器配置驱动的 NPC 私信定义。 */
public class NpcMessageDefinition {
    public enum DeliveryTrigger {
        INTERACTION,
        MANUAL,
        FOLLOW_UP
    }

    private String id = "";
    private int npcId;
    private String subject = "";
    private String content = "";
    private DeliveryTrigger trigger = DeliveryTrigger.MANUAL;
    private boolean once = true;
    private int priority;
    private int minimumFavorability = -1000;
    private int maximumFavorability = 1000;
    private ZhuiguangMembershipRequirement membershipRequirement = ZhuiguangMembershipRequirement.ANY;
    private List<NpcMessageReplyDefinition> replies = new ArrayList<>();
    private GuidanceSeed guidance;

    public NpcMessageDefinition() {
    }

    public NpcMessageDefinition(
            String id,
            int npcId,
            String subject,
            String content,
            DeliveryTrigger trigger) {
        this.id = id;
        this.npcId = npcId;
        this.subject = subject;
        this.content = content;
        this.trigger = trigger;
    }

    public String getId() {
        return id == null ? "" : id;
    }

    public int getNpcId() {
        return npcId;
    }

    public String getSubject() {
        return subject == null ? "" : subject;
    }

    public String getContent() {
        return content == null ? "" : content;
    }

    public DeliveryTrigger getTrigger() {
        return trigger == null ? DeliveryTrigger.MANUAL : trigger;
    }

    public boolean isOnce() {
        return once;
    }

    public int getPriority() {
        return priority;
    }

    public int getMinimumFavorability() {
        return minimumFavorability;
    }

    public int getMaximumFavorability() {
        return maximumFavorability;
    }

    public ZhuiguangMembershipRequirement getMembershipRequirement() {
        return membershipRequirement == null
                ? ZhuiguangMembershipRequirement.ANY
                : membershipRequirement;
    }

    public List<NpcMessageReplyDefinition> getReplies() {
        if (replies == null) {
            replies = new ArrayList<>();
        }
        return replies;
    }

    public GuidanceSeed getGuidance() {
        return guidance;
    }

    public boolean isAvailableAt(int favorability) {
        return favorability >= minimumFavorability && favorability <= maximumFavorability;
    }

    public boolean isAvailableFor(int favorability, boolean zhuiguangMember) {
        return isAvailableAt(favorability) && getMembershipRequirement().matches(zhuiguangMember);
    }

    public NpcMessageDefinition once(boolean once) {
        this.once = once;
        return this;
    }

    public NpcMessageDefinition priority(int priority) {
        this.priority = priority;
        return this;
    }

    public NpcMessageDefinition requiringFavorability(int minimum, int maximum) {
        this.minimumFavorability = minimum;
        this.maximumFavorability = maximum;
        return this;
    }

    public NpcMessageDefinition requiringMembership(ZhuiguangMembershipRequirement requirement) {
        this.membershipRequirement = requirement == null
                ? ZhuiguangMembershipRequirement.ANY
                : requirement;
        return this;
    }

    public NpcMessageDefinition withReplies(List<NpcMessageReplyDefinition> replies) {
        this.replies = replies == null ? new ArrayList<>() : new ArrayList<>(replies);
        return this;
    }

    public NpcMessageDefinition withGuidance(GuidanceSeed guidance) {
        this.guidance = guidance;
        return this;
    }

    /** 仅供内置文案的精确版本迁移使用；返回内容是否实际变化。 */
    boolean replaceText(String subject, String content) {
        String safeSubject = subject == null ? "" : subject;
        String safeContent = content == null ? "" : content;
        if (getSubject().equals(safeSubject) && getContent().equals(safeContent)) {
            return false;
        }
        this.subject = safeSubject;
        this.content = safeContent;
        return true;
    }
}
