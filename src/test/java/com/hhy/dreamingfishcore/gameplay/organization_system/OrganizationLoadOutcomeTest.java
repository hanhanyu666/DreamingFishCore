package com.hhy.dreamingfishcore.gameplay.organization_system;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 读档结果的分类策略。
 *
 * <p>这个分类**直接决定组织数据会不会被写成只读保护**：读档不可用时如果仍被判为 OK，
 * 内存里的空列表就会在关服时覆盖玩家存档，一次瞬时读取失败即可造成不可逆的数据丢失。
 * 所以把它抽成纯函数并在这里钉死。</p>
 */
class OrganizationLoadOutcomeTest {

    private static final Gson GSON = new Gson();

    @Test
    void usableDocumentIsOk() {
        assertEquals(OrganizationManager.LoadOutcome.OK,
                OrganizationManager.classify(new OrganizationDocument()),
                "默认空文档（文件不存在时走的就是这条路）必须视为正常");
    }

    @Test
    void missingDocumentIsFailure() {
        assertEquals(OrganizationManager.LoadOutcome.FAILED,
                OrganizationManager.classify(null),
                "读不出来时必须判失败，否则会被回写成空数据");
    }

    @Test
    void unknownSchemaVersionIsRefusedInsteadOfOverwritten() {
        OrganizationDocument newer = GSON.fromJson(
                "{\"schemaVersion\": 99, \"organizations\": []}", OrganizationDocument.class);
        assertEquals(OrganizationManager.LoadOutcome.UNSUPPORTED_SCHEMA,
                OrganizationManager.classify(newer),
                "用旧版本打开新存档只能只读，不能回写");

        OrganizationDocument older = GSON.fromJson(
                "{\"schemaVersion\": 0, \"organizations\": []}", OrganizationDocument.class);
        assertEquals(OrganizationManager.LoadOutcome.UNSUPPORTED_SCHEMA,
                OrganizationManager.classify(older));
    }

    @Test
    void writtenSchemaVersionMatchesWhatTheLoaderAccepts() {
        // 锁定 JSON 字段名：一旦改名，写出去的版本号读回来就会变成"不支持"，
        // 表现为升级后所有组织数据突然进入只读保护。
        String json = GSON.toJson(new OrganizationDocument());
        OrganizationDocument roundTripped = GSON.fromJson(json, OrganizationDocument.class);

        assertEquals(OrganizationDocument.CURRENT_SCHEMA_VERSION, roundTripped.getSchemaVersion(),
                "写盘用的版本号与读档校验的必须是同一个字段");
        assertEquals(OrganizationManager.LoadOutcome.OK,
                OrganizationManager.classify(roundTripped),
                "自己写出去的文件必须能被自己读回来");
    }

    @Test
    void emptyOrganizationsListIsStillAnOkDocument() {
        OrganizationDocument empty = GSON.fromJson(
                "{\"organizations\": []}", OrganizationDocument.class);
        assertEquals(OrganizationManager.LoadOutcome.OK,
                OrganizationManager.classify(empty),
                "缺省 schemaVersion 会取默认值，属于可处理的情况");
    }
}
