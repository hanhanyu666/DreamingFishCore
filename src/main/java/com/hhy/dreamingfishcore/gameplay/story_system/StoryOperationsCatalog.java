package com.hhy.dreamingfishcore.gameplay.story_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 可热重载的运营草稿。读取文件从不发布总结，也从不切换世界阶段。 */
public final class StoryOperationsCatalog {
    private StoryOperationsCatalog() { }

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(DreamingFishCore.MODID).resolve("story_operations.json");
    }

    public static Document read() {
        try {
            if (Files.notExists(path())) {
                JsonDataStore.writeAtomic(path(), StoryManager.GSON, Document.empty());
            }
            if (Files.size(path()) > 1_048_576) {
                throw new IllegalStateException("剧情运营配置超过 1 MiB");
            }
            Document candidate = StoryManager.GSON.fromJson(Files.readString(path(), StandardCharsets.UTF_8), Document.class);
            if (candidate == null) {
                throw new IllegalStateException("剧情运营配置不能为空");
            }
            candidate.validate();
            return candidate;
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取剧情运营配置：" + path(), exception);
        }
    }

    public record Document(int schemaVersion, List<RecapDraft> recaps, List<WorldTask> worldTasks) {
        public static Document empty() { return new Document(1, List.of(), List.of()); }

        public void validate() {
            if (schemaVersion != 1 || recaps == null || worldTasks == null
                    || recaps.size() > 128 || worldTasks.size() > 128) {
                throw new IllegalStateException("剧情运营配置版本或集合非法");
            }
            Set<String> ids = new HashSet<>();
            for (RecapDraft draft : recaps) {
                if (draft == null || !ids.add(draft.id())) {
                    throw new IllegalStateException("前情草稿为空或 ID 重复");
                }
                new StoryOperationsState.Recap(draft.id(), draft.title(), draft.content(),
                        draft.checkpointId(), -1000, 0).validate();
            }
            ids.clear();
            for (WorldTask task : worldTasks) {
                if (task == null || !ids.add(task.id())) {
                    throw new IllegalStateException("世界任务为空或 ID 重复");
                }
                StoryWorldState.requireValidId(task.stageId(), "世界任务阶段");
                task.definition().validateDefinition();
                task.gate();
                if (task.successFlag() != null && !task.successFlag().isBlank()) {
                    StoryWorldState.requireValidId(task.successFlag(), "成功旗标");
                }
            }
        }
    }

    public record RecapDraft(String id, String title, String content, String checkpointId) { }

    public record WorldTask(String id, int number, String stageId, String name, String content,
                            String locationId, List<String> prerequisiteTasks, int requiredPlayers,
                            String successFlag) {
        public StoryPlayerGate gate() { return new StoryPlayerGate(prerequisiteTasks, requiredPlayers); }

        public StoryTaskData definition() {
            if (name == null || name.isBlank() || name.length() > 256
                    || content == null || content.isBlank() || content.length() > 4096) {
                throw new IllegalArgumentException("世界任务文案超过限制：" + id);
            }
            StoryTaskData task = new StoryTaskData(id, number, name, content, 0, 0);
            task.setScope(StoryTaskData.Scope.WORLD);
            task.setPublishedByDefault(false);
            task.setLocationId(locationId);
            task.setGuidanceDefinitionIds(List.of(id));
            return task;
        }
    }
}
