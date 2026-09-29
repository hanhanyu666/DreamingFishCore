package com.hhy.dreamingfishcore.gameplay.organization_system;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 一个玩家组织的完整事实（持久化对象）。
 *
 * <p>所有字段都是**服务端权威**：客户端只接收快照，永远不直接改这些值。成员、申请、邀请
 * 一律以 UUID 字符串为键；同时缓存一份最近一次登录用的玩家名，供离线成员列表显示。</p>
 */
public final class Organization {

    /** 组织成员的记录。 */
    public static final class Member {
        private String lastName = "";
        private String rank = OrganizationRank.MEMBER.serializedName();
        private long joinedAtEpochMillis;

        public Member() {
        }

        public Member(String lastName, OrganizationRank rank, long joinedAtEpochMillis) {
            this.lastName = lastName == null ? "" : lastName;
            this.rank = rank == null ? OrganizationRank.MEMBER.serializedName() : rank.serializedName();
            this.joinedAtEpochMillis = joinedAtEpochMillis;
        }

        public String lastName() {
            return lastName == null ? "" : lastName;
        }

        public void setLastName(String value) {
            this.lastName = value == null ? "" : value;
        }

        public OrganizationRank rank() {
            return OrganizationRank.parse(rank);
        }

        public void setRank(OrganizationRank value) {
            this.rank = (value == null ? OrganizationRank.MEMBER : value).serializedName();
        }

        public long joinedAtEpochMillis() {
            return joinedAtEpochMillis;
        }
    }

    private String id = "";
    private String name = "";
    private String leaderId = "";
    private String announcement = "";
    private long createdAtEpochMillis;
    /**
     * 创建这个组织**实际支付**的梦鱼币；0 表示没收费（免费创建，或当时经济服务不可用）。
     *
     * <p>解散退款按这个值算而不是按当前配置：否则在经济服务不可用时免费建的组织，
     * 解散时反而能白拿一笔钱。旧存档没有这个字段，反序列化后为 0，是正确语义。</p>
     */
    private int creationCostPaid;
    /** 成员：UUID 字符串 → 成员记录。 */
    private Map<String, Member> members = new LinkedHashMap<>();
    /** 待审批的入会申请：UUID 字符串 → 申请时间。 */
    private Map<String, Long> applications = new LinkedHashMap<>();
    /** 已发出、等待对方接受的邀请：UUID 字符串 → 邀请时间。 */
    private Map<String, Long> invites = new LinkedHashMap<>();

    public Organization() {
    }

    public Organization(String id, String name, String leaderId, String leaderName,
                        long createdAtEpochMillis) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.leaderId = leaderId == null ? "" : leaderId;
        this.createdAtEpochMillis = createdAtEpochMillis;
        this.members = new LinkedHashMap<>();
        if (!this.leaderId.isEmpty()) {
            members.put(this.leaderId,
                    new Member(leaderName, OrganizationRank.LEADER, createdAtEpochMillis));
        }
    }

    public String id() {
        return id == null ? "" : id;
    }

    public void setId(String value) {
        this.id = value == null ? "" : value;
    }

    public String name() {
        return name == null ? "" : name;
    }

    public void setName(String value) {
        this.name = value == null ? "" : value;
    }

    public String leaderId() {
        return leaderId == null ? "" : leaderId;
    }

    public void setLeaderId(String value) {
        this.leaderId = value == null ? "" : value;
    }

    public String announcement() {
        return announcement == null ? "" : announcement;
    }

    public void setAnnouncement(String value) {
        this.announcement = value == null ? "" : value;
    }

    public long createdAtEpochMillis() {
        return createdAtEpochMillis;
    }

    /** 创建时实际支付的梦鱼币；0 表示免费创建。 */
    public int creationCostPaid() {
        return Math.max(0, creationCostPaid);
    }

    public void setCreationCostPaid(int value) {
        this.creationCostPaid = Math.max(0, value);
    }

    public Map<String, Member> members() {
        if (members == null) {
            members = new LinkedHashMap<>();
        }
        return members;
    }

    public Map<String, Long> applications() {
        if (applications == null) {
            applications = new LinkedHashMap<>();
        }
        return applications;
    }

    public Map<String, Long> invites() {
        if (invites == null) {
            invites = new LinkedHashMap<>();
        }
        return invites;
    }

    public int memberCount() {
        return members().size();
    }

    public boolean isMember(UUID playerId) {
        return playerId != null && members().containsKey(playerId.toString());
    }

    /** 成员职位；不是成员时返回空。 */
    public Optional<OrganizationRank> rankOf(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        Member member = members().get(playerId.toString());
        return member == null ? Optional.empty() : Optional.of(member.rank());
    }

    public boolean isLeader(UUID playerId) {
        return playerId != null && leaderId().equals(playerId.toString());
    }

    public void addMember(UUID playerId, String lastName, OrganizationRank rank, long now) {
        if (playerId == null) {
            return;
        }
        members().put(playerId.toString(), new Member(lastName, rank, now));
        applications().remove(playerId.toString());
        invites().remove(playerId.toString());
    }

    public boolean removeMember(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        boolean removed = members().remove(playerId.toString()) != null;
        applications().remove(playerId.toString());
        invites().remove(playerId.toString());
        return removed;
    }

    /** 刷新缓存显示名（玩家登录时调用）。 */
    public void refreshMemberName(UUID playerId, String lastName) {
        if (playerId == null) {
            return;
        }
        Member member = members().get(playerId.toString());
        if (member != null) {
            member.setLastName(lastName);
        }
    }

    /**
     * 成员按职位从高到低、加入时间从早到晚排序。
     *
     * <p>列表顺序在服务端固定下来，客户端不必再次排序，也避免两边排序规则漂移。</p>
     */
    public List<Map.Entry<String, Member>> sortedMembers() {
        List<Map.Entry<String, Member>> entries = new ArrayList<>(members().entrySet());
        entries.sort(Comparator
                .comparingInt((Map.Entry<String, Member> entry) -> -entry.getValue().rank().weight())
                .thenComparingLong(entry -> entry.getValue().joinedAtEpochMillis()));
        return entries;
    }

    /** 找到除会长外职位最高的成员，用于会长退出时自动继任。 */
    public Optional<UUID> highestRankedSuccessor() {
        return sortedMembers().stream()
                .filter(entry -> !entry.getKey().equals(leaderId()))
                .map(entry -> safeUuid(entry.getKey()))
                .flatMap(Optional::stream)
                .findFirst();
    }

    /** 校验持久化对象自洽；损坏条目应在加载阶段被拒绝，而不是让运行时到处判空。 */
    public void validate() {
        if (id().isBlank()) {
            throw new IllegalStateException("组织缺少 id");
        }
        if (name().isBlank()) {
            throw new IllegalStateException("组织缺少名称：" + id());
        }
        if (leaderId().isBlank()) {
            throw new IllegalStateException("组织缺少会长：" + id());
        }
        Member leader = members().get(leaderId());
        if (leader == null) {
            throw new IllegalStateException("会长不在成员列表中：" + id());
        }
        if (leader.rank() != OrganizationRank.LEADER) {
            throw new IllegalStateException("会长职位不是 LEADER：" + id());
        }
        if (creationCostPaid < 0) {
            throw new IllegalStateException("创建费为负数：" + id());
        }
        members().forEach((key, member) -> {
            if (member == null) {
                throw new IllegalStateException("组织包含空的成员记录：" + id());
            }
            if (safeUuid(key).isEmpty()) {
                throw new IllegalStateException("组织成员 UUID 非法：" + key);
            }
        });
    }

    private static Optional<UUID> safeUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
