package com.hhy.dreamingfishcore.gameplay.clue_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.init.CommonInit;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 线索目录：玩家可见的 {@link ClueDefinition} + 服主私密的 {@link ClueSecrets}。
 *
 * <p>两份内容来源不同：</p>
 * <ul>
 *   <li><b>可见</b>：内置默认在模组资源里（随 jar 分发，所以只有玩家能看的东西），
 *       运行期读 {@code config/dreamingfishcore/data/clues.json}，缺失时用内置写一份。</li>
 *   <li><b>私密</b>：只在 {@code config/dreamingfishcore/clue_secrets.json}，
 *       <b>绝不进模组资源</b>；文件缺失时按"没有任何私密信息"处理，不自动创建（避免把模板写进分发物）。</li>
 * </ul>
 *
 * <p>校验失败时清空目录并进入只读保护（与随记本、任务地点同一套失败语义）：
 * 宁可不提供线索，也不能让半份坏内容进入运行内存。</p>
 */
public final class ClueCatalog {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String VISIBLE_FILE_NAME = "clues.json";
    public static final String SECRETS_FILE_NAME = "clue_secrets.json";
    private static final String BUILT_IN_RESOURCE = "/dreamingfishcore/defaults/clues.json";
    public static final int MAX_CLUES = 16384;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();
    private static final Type CLUE_LIST_TYPE = new TypeToken<List<ClueDefinition>>() {
    }.getType();

    private static final Map<String, ClueDefinition> BY_ID = new LinkedHashMap<>();
    private static final Map<String, ClueSecrets> SECRETS_BY_ID = new LinkedHashMap<>();
    private static final Map<Integer, String> BY_LEGACY_ID = new LinkedHashMap<>();

    private static boolean loaded;
    private static boolean readOnly;
    /** 内置默认内容的可用性（读不到时仍然允许用服主自己写的文件）。 */
    private static boolean builtInAvailable;

    private ClueCatalog() {
    }

    // ==================== 路径 ====================

    public static Path visiblePath() {
        return CommonInit.CONFIG_DIRECTORY.resolve("data").resolve(VISIBLE_FILE_NAME);
    }

    public static Path secretsPath() {
        return CommonInit.CONFIG_DIRECTORY.resolve(SECRETS_FILE_NAME);
    }

    // ==================== 加载 ====================

    /** 启动时调用；失败进只读保护，不抛给调用方。 */
    public static synchronized void load() {
        BY_ID.clear();
        SECRETS_BY_ID.clear();
        BY_LEGACY_ID.clear();
        loaded = false;
        readOnly = false;

        Map<String, ClueDefinition> visible = new LinkedHashMap<>();
        try {
            List<ClueDefinition> builtIn = readBuiltIn();
            builtInAvailable = builtIn != null;
            Path path = visiblePath();
            if (builtInAvailable && !Files.exists(path)) {
                writeVisibleDefault(builtIn);
            }
            if (Files.exists(path) && Files.size(path) == 0L) {
                throw new IllegalStateException("线索内容文件为空，拒绝覆盖：" + path);
            }
            List<ClueDefinition> fromFile = JsonDataStore.read(path, GSON, CLUE_LIST_TYPE,
                    ArrayList::new);
            if ((fromFile == null || fromFile.isEmpty()) && builtInAvailable) {
                // 解析出空数组：把内置默认写回去，避免"内容全没了"。
                writeVisibleDefault(builtIn);
                fromFile = new ArrayList<>(builtIn);
            }
            resolveDefinitions(fromFile == null ? List.of() : fromFile, visible);
            validateVisible(visible);

            Map<String, ClueSecrets> secrets = loadSecrets();
            validateSecrets(visible, secrets);

            BY_ID.putAll(visible);
            SECRETS_BY_ID.putAll(secrets);
            for (ClueDefinition definition : visible.values()) {
                if (definition.legacyId() > 0) {
                    BY_LEGACY_ID.put(definition.legacyId(), definition.id());
                }
            }
            loaded = true;
            DreamingFishCore.LOGGER.info("线索目录加载完成：{} 条（其中 {} 条带旧编号，私密定义 {} 条）",
                    BY_ID.size(), BY_LEGACY_ID.size(), SECRETS_BY_ID.size());
        } catch (IOException | RuntimeException exception) {
            BY_ID.clear();
            SECRETS_BY_ID.clear();
            BY_LEGACY_ID.clear();
            loaded = true;
            readOnly = true;
            DreamingFishCore.LOGGER.error("线索目录加载失败，本次启动不提供线索（旧文件保留）", exception);
        }
    }

    /**
     * 把文件里的条目归一成"稳定 ID 优先"：只有旧编号的旧格式条目自动补出稳定 ID。
     *
     * <p>旧格式（只有 {@code id} 整数）在升级后仍能被读到，靠 {@code legacyId} 找回；
     * 但新格式必须显式写稳定 ID —— 不然"稳定 ID"就白做了。</p>
     */
    private static void resolveDefinitions(List<ClueDefinition> raw,
                                           Map<String, ClueDefinition> target) {
        for (ClueDefinition definition : raw) {
            if (definition == null) {
                throw new IllegalStateException("线索内容包含空条目");
            }
            definition.validate();
            if (!target.containsKey(definition.id())) {
                target.put(definition.id(), definition);
            } else {
                throw new IllegalStateException("线索 ID 重复：" + definition.id());
            }
        }
    }

    private static void validateVisible(Map<String, ClueDefinition> visible) {
        if (visible.size() > MAX_CLUES) {
            throw new IllegalStateException("线索数量超过上限：" + visible.size());
        }
        Map<Integer, String> legacy = new LinkedHashMap<>();
        for (ClueDefinition definition : visible.values()) {
            if (definition.legacyId() > 0) {
                String previous = legacy.putIfAbsent(definition.legacyId(), definition.id());
                if (previous != null) {
                    throw new IllegalStateException("线索旧编号重复：" + definition.legacyId()
                            + "（" + previous + " / " + definition.id() + "）");
                }
            }
        }
    }

    private static Map<String, ClueSecrets> loadSecrets() throws IOException {
        Path path = secretsPath();
        Map<String, ClueSecrets> secrets = new LinkedHashMap<>();
        if (!Files.exists(path)) {
            // 私密文件不是必需品：没有就当"没有立场/证据包/关系信息"，也不主动写模板。
            DreamingFishCore.LOGGER.info("未找到线索私密定义（{}），本次按无隐藏信息运行", path);
            return secrets;
        }
        List<ClueSecrets> list = JsonDataStore.read(path, GSON,
                new TypeToken<List<ClueSecrets>>() {
                }.getType(), ArrayList::new);
        for (ClueSecrets entry : list == null ? List.<ClueSecrets>of() : list) {
            if (entry == null) {
                continue;
            }
            entry.validate();
            if (secrets.putIfAbsent(entry.id(), entry) != null) {
                throw new IllegalStateException("线索私密定义 ID 重复：" + entry.id());
            }
        }
        return secrets;
    }

    private static void validateSecrets(Map<String, ClueDefinition> visible,
                                        Map<String, ClueSecrets> secrets) {
        for (ClueSecrets entry : secrets.values()) {
            if (!visible.containsKey(entry.id())) {
                // 内容删了但私密定义还在：只记警告，不因此整份目录失效。
                DreamingFishCore.LOGGER.warn("线索私密定义指向不存在的线索，已忽略：{}", entry.id());
            }
        }
    }

    private static List<ClueDefinition> readBuiltIn() {
        try (InputStream stream = ClueCatalog.class.getResourceAsStream(BUILT_IN_RESOURCE)) {
            if (stream == null) {
                DreamingFishCore.LOGGER.warn("内置线索默认内容缺失：{}", BUILT_IN_RESOURCE);
                return null;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                List<ClueDefinition> parsed = GSON.fromJson(reader, CLUE_LIST_TYPE);
                return parsed == null ? List.of() : parsed;
            }
        } catch (IOException | RuntimeException exception) {
            DreamingFishCore.LOGGER.warn("内置线索默认内容读取失败：{}", BUILT_IN_RESOURCE, exception);
            return null;
        }
    }

    private static void writeVisibleDefault(List<ClueDefinition> builtIn) throws IOException {
        JsonDataStore.writeAtomic(visiblePath(), GSON, builtIn);
        DreamingFishCore.LOGGER.info("已写出默认线索内容：{}（{} 条）", visiblePath(), builtIn.size());
    }

    // ==================== 查询 ====================

    public static synchronized boolean isLoaded() {
        return loaded;
    }

    public static synchronized boolean isReadOnly() {
        return readOnly;
    }

    public static synchronized List<ClueDefinition> all() {
        return List.copyOf(BY_ID.values());
    }

    public static synchronized int count() {
        return BY_ID.size();
    }

    public static synchronized ClueDefinition byId(String clueId) {
        return clueId == null ? null : BY_ID.get(clueId);
    }

    public static synchronized boolean has(String clueId) {
        return clueId != null && BY_ID.containsKey(clueId);
    }

    /** 旧整数编号 → 稳定 ID；找不到返回 null。 */
    public static synchronized String idForLegacy(int legacyId) {
        return BY_LEGACY_ID.get(legacyId);
    }

    public static synchronized ClueDefinition byLegacyId(int legacyId) {
        String id = BY_LEGACY_ID.get(legacyId);
        return id == null ? null : BY_ID.get(id);
    }

    /** 服主私密定义；没有返回 null（表示这条线索没有隐藏信息）。 */
    public static synchronized ClueSecrets secretsOf(String clueId) {
        return clueId == null ? null : SECRETS_BY_ID.get(clueId);
    }

    public static synchronized List<ClueSecrets> allSecrets() {
        return List.copyOf(SECRETS_BY_ID.values());
    }

    /** 某个证据包里的线索（服主视角，不用于客户端视图）。 */
    public static synchronized List<String> cluesInEvidencePack(String packId) {
        if (packId == null || packId.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (ClueSecrets entry : SECRETS_BY_ID.values()) {
            if (packId.equals(entry.evidencePackId())) {
                result.add(entry.id());
            }
        }
        return List.copyOf(result);
    }

    /** 仅测试用：直接注入一份内容，走与真实加载完全相同的校验路径。 */
    static synchronized void installForTest(List<ClueDefinition> definitions,
                                            List<ClueSecrets> secrets) {
        Map<String, ClueDefinition> visible = new LinkedHashMap<>();
        resolveDefinitions(definitions, visible);
        validateVisible(visible);
        Map<String, ClueSecrets> secretMap = new LinkedHashMap<>();
        for (ClueSecrets entry : secrets) {
            entry.validate();
            secretMap.put(entry.id(), entry);
        }

        BY_ID.clear();
        SECRETS_BY_ID.clear();
        BY_LEGACY_ID.clear();
        BY_ID.putAll(visible);
        SECRETS_BY_ID.putAll(secretMap);
        for (ClueDefinition definition : visible.values()) {
            if (definition.legacyId() > 0) {
                BY_LEGACY_ID.put(definition.legacyId(), definition.id());
            }
        }
        loaded = true;
        readOnly = false;
    }

    static synchronized void resetForTest() {
        BY_ID.clear();
        SECRETS_BY_ID.clear();
        BY_LEGACY_ID.clear();
        loaded = false;
        readOnly = false;
        builtInAvailable = false;
    }

    /** 仅测试用：解析一段 JSON 文本。 */
    static List<ClueDefinition> parseForTest(String json) {
        List<ClueDefinition> parsed = GSON.fromJson(json, CLUE_LIST_TYPE);
        return parsed == null ? List.of() : parsed;
    }
}
