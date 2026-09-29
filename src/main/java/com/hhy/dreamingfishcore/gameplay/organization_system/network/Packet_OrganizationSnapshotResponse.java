package com.hhy.dreamingfishcore.gameplay.organization_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.organization_system.OrganizationViewData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端下发的组织只读快照。
 *
 * <p>权限开关（能否审批/邀请/编辑公告/管理人员）由服务端算好，客户端只负责按开关画界面。</p>
 */
public record Packet_OrganizationSnapshotResponse(OrganizationViewData.Snapshot snapshot)
        implements CustomPacketPayload {

    private static final int MAX_ORGANIZATIONS = 4096;
    private static final int MAX_LINES = 512;

    public static final Type<Packet_OrganizationSnapshotResponse> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    DreamingFishCore.MODID, "organization/snapshot_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_OrganizationSnapshotResponse>
            STREAM_CODEC = StreamCodec.of(
                    Packet_OrganizationSnapshotResponse::encode,
                    Packet_OrganizationSnapshotResponse::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer,
                               Packet_OrganizationSnapshotResponse packet) {
        OrganizationViewData.Snapshot snapshot = packet.snapshot();
        buffer.writeBoolean(snapshot.enabled());
        buffer.writeVarInt(snapshot.maxMembers());
        buffer.writeVarInt(snapshot.nameMaxLength());
        buffer.writeVarInt(snapshot.announcementMaxLength());
        buffer.writeVarInt(snapshot.creationCost());
        buffer.writeUtf(snapshot.myOrganizationId(), 64);

        buffer.writeVarInt(snapshot.organizations().size());
        for (OrganizationViewData.Summary summary : snapshot.organizations()) {
            buffer.writeUtf(summary.id(), 64);
            buffer.writeUtf(summary.name(), 64);
            buffer.writeVarInt(summary.memberCount());
            buffer.writeUtf(summary.leaderName(), 64);
            buffer.writeEnum(summary.relation());
        }

        OrganizationViewData.Detail detail = snapshot.myOrganization();
        buffer.writeBoolean(detail != null);
        if (detail != null) {
            buffer.writeUtf(detail.id(), 64);
            buffer.writeUtf(detail.name(), 64);
            buffer.writeUtf(detail.announcement(), 2048);
            buffer.writeUtf(detail.myRankId(), 32);
            buffer.writeUtf(detail.myRankName(), 32);
            buffer.writeBoolean(detail.canReviewApplications());
            buffer.writeBoolean(detail.canInvite());
            buffer.writeBoolean(detail.canEditAnnouncement());
            buffer.writeBoolean(detail.canManageMembers());
            writeLines(buffer, detail.members());
            writeLines(buffer, detail.applicants());
            writeLines(buffer, detail.invited());
            buffer.writeLong(detail.createdAtEpochMillis());
        }
    }

    private static void writeLines(RegistryFriendlyByteBuf buffer,
                                   List<OrganizationViewData.MemberLine> lines) {
        buffer.writeVarInt(lines.size());
        for (OrganizationViewData.MemberLine line : lines) {
            buffer.writeUtf(line.playerId(), 64);
            buffer.writeUtf(line.name(), 64);
            buffer.writeUtf(line.rankId(), 32);
            buffer.writeUtf(line.rankName(), 32);
            buffer.writeBoolean(line.online());
        }
    }

    private static Packet_OrganizationSnapshotResponse decode(RegistryFriendlyByteBuf buffer) {
        boolean enabled = buffer.readBoolean();
        int maxMembers = buffer.readVarInt();
        int nameMaxLength = buffer.readVarInt();
        int announcementMaxLength = buffer.readVarInt();
        int creationCost = buffer.readVarInt();
        String myOrganizationId = buffer.readUtf(64);

        int organizationCount = Math.min(buffer.readVarInt(), MAX_ORGANIZATIONS);
        List<OrganizationViewData.Summary> organizations = new ArrayList<>(organizationCount);
        for (int index = 0; index < organizationCount; index++) {
            organizations.add(new OrganizationViewData.Summary(
                    buffer.readUtf(64),
                    buffer.readUtf(64),
                    buffer.readVarInt(),
                    buffer.readUtf(64),
                    buffer.readEnum(OrganizationViewData.Relation.class)));
        }

        OrganizationViewData.Detail detail = null;
        if (buffer.readBoolean()) {
            String id = buffer.readUtf(64);
            String name = buffer.readUtf(64);
            String announcement = buffer.readUtf(2048);
            String myRankId = buffer.readUtf(32);
            String myRankName = buffer.readUtf(32);
            boolean canReview = buffer.readBoolean();
            boolean canInvite = buffer.readBoolean();
            boolean canEditAnnouncement = buffer.readBoolean();
            boolean canManageMembers = buffer.readBoolean();
            List<OrganizationViewData.MemberLine> members = readLines(buffer);
            List<OrganizationViewData.MemberLine> applicants = readLines(buffer);
            List<OrganizationViewData.MemberLine> invited = readLines(buffer);
            long createdAt = buffer.readLong();
            detail = new OrganizationViewData.Detail(id, name, announcement, myRankId, myRankName,
                    canReview, canInvite, canEditAnnouncement, canManageMembers,
                    members, applicants, invited, createdAt);
        }

        return new Packet_OrganizationSnapshotResponse(new OrganizationViewData.Snapshot(
                enabled, maxMembers, nameMaxLength, announcementMaxLength,
                creationCost, myOrganizationId, List.copyOf(organizations), detail));
    }

    private static List<OrganizationViewData.MemberLine> readLines(RegistryFriendlyByteBuf buffer) {
        int count = Math.min(buffer.readVarInt(), MAX_LINES);
        List<OrganizationViewData.MemberLine> lines = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            lines.add(new OrganizationViewData.MemberLine(
                    buffer.readUtf(64),
                    buffer.readUtf(64),
                    buffer.readUtf(32),
                    buffer.readUtf(32),
                    buffer.readBoolean()));
        }
        return List.copyOf(lines);
    }

    public static void handle(Packet_OrganizationSnapshotResponse packet, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(packet));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(Packet_OrganizationSnapshotResponse packet) {
        com.hhy.dreamingfishcore.gameplay.organization_system.client.cache.OrganizationClientCache
                .set(packet.snapshot());
    }
}
