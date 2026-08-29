package com.hhy.dreamingfishcore.gameplay.zhuiguang_system;

/** NPC消息及回复对玩家当前逐光会成员身份的可见条件。 */
public enum ZhuiguangMembershipRequirement {
    ANY,
    MEMBER,
    NON_MEMBER;

    public boolean matches(boolean member) {
        return switch (this) {
            case ANY -> true;
            case MEMBER -> member;
            case NON_MEMBER -> !member;
        };
    }
}
