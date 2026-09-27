package com.hhy.dreamingfishcore.gameplay.task_system;

import com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryTaskData;
import com.hhy.dreamingfishcore.gameplay.task_system.client.cache.TaskClientCache;
import com.hhy.dreamingfishcore.gameplay.task_system.network.Packet_SyncFullTaskData;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class StoryTaskPacketTest {
    @Test
    void archivedAndWaivedFlagsRoundTripWithoutInventingPersonalCompletion() {
        StoryStageData stage = new StoryStageData("test:stage", 1, "阶段", "介绍");
        stage.setCurrentStage(true);
        StoryTaskData skipped = new StoryTaskData("test:skipped", 1, "旧步骤", "说明", 0, 0);
        skipped.setPersonalTask(true);
        skipped.setWaived(true);
        StoryTaskData failed = new StoryTaskData("test:failed", 2, "旧世界任务", "说明", 0, 0);
        failed.setCompleted(true);
        failed.setFailed(true);
        failed.setArchived(true);
        stage.addTask(skipped);
        stage.addTask(failed);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            Packet_SyncFullTaskData.encode(new Packet_SyncFullTaskData(UUID.randomUUID(), Map.of(), Map.of(1, stage)), buffer);
            var decoded = Packet_SyncFullTaskData.decode(buffer);
            var tasks = decoded.getStoryStageData().get(1).getTasks();
            assertTrue(tasks.getFirst().isWaived());
            assertFalse(tasks.getFirst().isClientPlayerFinished());
            assertTrue(tasks.get(1).isArchived());
            assertTrue(tasks.get(1).isFailed());
            assertFalse(tasks.get(1).isClientPlayerFinished());
            TaskClientCache.update(Map.of(), decoded.getStoryStageData());
            assertFalse(TaskClientCache.hasUnfinishedTasks());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
            TaskClientCache.clear();
        }
    }
}
