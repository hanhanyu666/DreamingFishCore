package com.hhy.dreamingfishcore.gameplay.organization_system;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 组织配置：创建费与解散退款比例的默认值、越界修正、退款算法。 */
class OrganizationConfigTest {

    private static final Gson GSON = new Gson();

    private static Path write(Path dir, String json) throws Exception {
        Path path = dir.resolve("organization.json");
        Files.writeString(path, json, StandardCharsets.UTF_8);
        return path;
    }

    @Test
    void missingFileCreatesDefaultsWithCost150AndHalfRefund(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("organization.json");

        OrganizationConfig config = OrganizationConfig.load(path);

        assertTrue(Files.exists(path), "缺失配置应写入默认文件");
        assertEquals(150, config.getCreationCost(), "默认创建费应为 150 梦鱼币");
        assertEquals(50, config.getDisbandRefundPercent(), "默认解散退款比例应为一半");
    }

    @Test
    void configuredCostAndRefundAreHonoured(@TempDir Path dir) throws Exception {
        Path path = write(dir, """
                {"schemaVersion": 1, "enabled": true, "maxOrganizations": 64, "maxMembers": 32,
                 "nameMinLength": 2, "nameMaxLength": 12, "announcementMaxLength": 200,
                 "creationCost": 800, "disbandRefundPercent": 25}
                """);

        OrganizationConfig config = OrganizationConfig.load(path);

        assertEquals(800, config.getCreationCost());
        assertEquals(25, config.getDisbandRefundPercent());
        assertEquals(200, config.refundFor(800));
    }

    @Test
    void zeroCostMeansFreeCreation(@TempDir Path dir) throws Exception {
        Path path = write(dir, """
                {"schemaVersion": 1, "enabled": true, "creationCost": 0, "disbandRefundPercent": 50}
                """);

        OrganizationConfig config = OrganizationConfig.load(path);

        assertEquals(0, config.getCreationCost(), "0 表示免费创建");
        assertEquals(0, config.refundFor(0), "没付过钱就没有退款");
    }

    @Test
    void outOfRangeValuesAreClampedAndWrittenBack(@TempDir Path dir) throws Exception {
        Path path = write(dir, """
                {"schemaVersion": 1, "enabled": true,
                 "creationCost": -50, "disbandRefundPercent": 250}
                """);

        OrganizationConfig config = OrganizationConfig.load(path);

        assertEquals(0, config.getCreationCost(), "负数创建费应修正为 0");
        assertEquals(100, config.getDisbandRefundPercent(), "超过 100% 的退款比例应修正为 100");

        String writtenBack = Files.readString(path, StandardCharsets.UTF_8);
        assertTrue(writtenBack.contains("\"creationCost\": 0"), "修正结果应写回文件：" + writtenBack);
        assertTrue(writtenBack.contains("\"disbandRefundPercent\": 100"), writtenBack);
    }

    @Test
    void refundIsBasedOnPaidAmountNotOnCurrentConfig() {
        // 关键性质：组织解散退的是"当初实际付了多少"的一半。
        // 否则在经济服务不可用时免费建的组织，解散反而能白拿一笔。
        OrganizationConfig half = GSON.fromJson("{\"disbandRefundPercent\": 50}", OrganizationConfig.class);

        assertEquals(0, half.refundFor(0), "免费建的组织解散不能退款");
        assertEquals(75, half.refundFor(150));
        assertEquals(400, half.refundFor(800));
    }

    @Test
    void refundRoundsDownAndNeverExceedsPaid() {
        OrganizationConfig half = GSON.fromJson("{\"disbandRefundPercent\": 50}", OrganizationConfig.class);
        OrganizationConfig everything = GSON.fromJson("{\"disbandRefundPercent\": 100}", OrganizationConfig.class);
        OrganizationConfig nothing = GSON.fromJson("{\"disbandRefundPercent\": 0}", OrganizationConfig.class);

        assertEquals(0, half.refundFor(1), "1 的一半向下取整为 0，不能凭空多给");
        assertEquals(1, half.refundFor(3));
        assertEquals(150, everything.refundFor(150), "最多退实付全款");
        assertEquals(0, nothing.refundFor(150), "比例为 0 时一分不退");
        assertEquals(0, half.refundFor(-100), "负数实付按 0 处理");
    }

    @Test
    void legacyConfigWithoutNewKeysGetsThemWrittenBack(@TempDir Path dir) throws Exception {
        // 旧版本生成的配置文件里没有创建费相关键；值会用默认值，但必须补进文件让服主看得见。
        Path path = write(dir, """
                {"schemaVersion": 1, "enabled": true, "maxOrganizations": 64, "maxMembers": 32,
                 "nameMinLength": 2, "nameMaxLength": 12, "announcementMaxLength": 200}
                """);

        OrganizationConfig config = OrganizationConfig.load(path);

        assertEquals(150, config.getCreationCost());
        assertEquals(50, config.getDisbandRefundPercent());
        String writtenBack = Files.readString(path, StandardCharsets.UTF_8);
        assertTrue(writtenBack.contains("\"creationCost\""), "新键应被补进旧配置：" + writtenBack);
        assertTrue(writtenBack.contains("\"disbandRefundPercent\""), writtenBack);
    }

    @Test
    void organizationRecordsPaidCostWithDefaultZero() {
        Organization organization = new Organization("id", "测试", "leader-uuid", "会长", 0L);
        assertEquals(0, organization.creationCostPaid(),
                "旧存档没有这个字段，读出来应为 0，等价于「没付过钱」");

        organization.setCreationCostPaid(150);
        assertEquals(150, organization.creationCostPaid());

        organization.setCreationCostPaid(-1);
        assertEquals(0, organization.creationCostPaid(), "负数实付应被收敛为 0");
    }
}
