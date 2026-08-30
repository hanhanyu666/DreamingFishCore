package com.hhy.dreamingfishcore.gameplay.story_system.runtime;

import com.hhy.dreamingfishcore.gameplay.story_system.OpeningStoryDefinitionCatalog;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryStageCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryFlowDefinitionTest {
    @Test
    void bundledOpeningFlowUsesStableFactsAndDeclarativeEffects() {
        StoryFlowDefinitionDocument document = StoryFlowDefinitionStore.createDefaultDocument();
        StoryFlowDefinitionStore.validate(
                document,
                Set.of(
                        StoryStageCatalog.DREAM_BEGINNING_ID,
                        StoryStageCatalog.AFTERDREAM_ID,
                        StoryStageCatalog.CONTROL_PERIOD_ID,
                        StoryStageCatalog.LIGHT_DOUBT_ID,
                        StoryStageCatalog.DAWN_ID));

        StoryFlowDefinition flow = document.getFlows().get(0);
        assertEquals(2, document.getSchemaVersion());
        assertEquals("read_abydos_notice", flow.getInitialNodeId());
        assertEquals(9, flow.getNodes().size());
        StoryFlowNode locationNode = flow.getNodes().stream()
                .filter(node -> node.getEvent() == StoryEventType.LOCATION_ENTERED)
                .findFirst()
                .orElseThrow();
        assertEquals(OpeningStoryDefinitionCatalog.ABYDOS_LOCATION_ID,
                locationNode.getLocationId());
        assertEquals("enter_abydos", locationNode.getCondition("cursor"));
        assertTrue(locationNode.getEffects().stream()
                .anyMatch(effect -> "SEND_NPC_MESSAGE".equalsIgnoreCase(effect.getType())));
        assertTrue(locationNode.getEffects().stream()
                .filter(effect -> "RECORD_PERSONAL_TASK".equalsIgnoreCase(effect.getType()))
                .anyMatch(effect -> OpeningStoryDefinitionCatalog.SETTLE_IN_ABYDOS_TASK_ID
                        .equals(effect.getParameter("taskId"))));
    }

    @Test
    void defaultDocumentComesFromTheBundledOpeningGraph() {
        StoryFlowDefinitionDocument bundled = StoryFlowDefinitionStore.loadBundledDefault();
        StoryFlowDefinitionDocument defaults = StoryFlowDefinitionStore.createDefaultDocument();

        StoryFlowDefinitionStore.validate(
                bundled, Set.of(StoryStageCatalog.DREAM_BEGINNING_ID));
        assertEquals(defaults.getSchemaVersion(), bundled.getSchemaVersion());
        assertEquals(
                defaults.getFlows().get(0).getNodes().stream()
                        .map(StoryFlowNode::getId).toList(),
                bundled.getFlows().get(0).getNodes().stream()
                        .map(StoryFlowNode::getId).toList());
        assertEquals(
                defaults.getFlows().get(0).getNodes().stream()
                        .map(node -> node.getEffects().size()).toList(),
                bundled.getFlows().get(0).getNodes().stream()
                        .map(node -> node.getEffects().size()).toList());
        assertEquals(
                defaults.getFlows().get(0).getNodes().stream()
                        .flatMap(node -> node.getEffects().stream())
                        .map(StoryFlowEffect::getId).toList(),
                bundled.getFlows().get(0).getNodes().stream()
                        .flatMap(node -> node.getEffects().stream())
                        .map(StoryFlowEffect::getId).toList());
    }

    @Test
    void nodeMatchingDoesNotTreatDisplayNamesAsFacts() {
        StoryFlowNode node = new StoryFlowNode(
                "enter",
                StoryEventType.LOCATION_ENTERED,
                "", "", OpeningStoryDefinitionCatalog.ABYDOS_LOCATION_ID,
                StoryFlowScope.PLAYER,
                List.of(new StoryFlowEffect("SYNC_PLAYER")));

        assertTrue(node.matches(StoryEvent.locationEntered(
                null, OpeningStoryDefinitionCatalog.ABYDOS_LOCATION_ID, "阿拜多斯")));
        assertFalse(node.matches(StoryEvent.locationEntered(
                null, "阿拜多斯", "阿拜多斯")));
    }

    @Test
    void invalidStageReferenceIsRejectedBeforeInstall() {
        StoryFlowDefinitionDocument document = StoryFlowDefinitionStore.createDefaultDocument();
        assertThrows(IllegalStateException.class, () -> StoryFlowDefinitionStore.validate(
                document, Set.of(StoryStageCatalog.AFTERDREAM_ID)));
    }

    @Test
    void customEffectMayBeDeclaredBeforeItsServerExecutorExists() {
        StoryFlowNode node = new StoryFlowNode(
                "custom_effect",
                StoryEventType.NOTICE_READ,
                "opening.desert_town",
                "", "", StoryFlowScope.PLAYER,
                List.of(new StoryFlowEffect("RECORD_CLUE_V2", Map.of("key", "value"))))
                .withCondition("cursor", "custom_effect");
        StoryFlowDefinition definition = new StoryFlowDefinition(
                "dreamingfishcore:flow/custom",
                StoryStageCatalog.DREAM_BEGINNING_ID,
                StoryFlowScope.PLAYER,
                "custom_effect",
                List.of(node));

        StoryFlowDefinitionStore.validate(
                new StoryFlowDefinitionDocument(List.of(definition)),
                Set.of(StoryStageCatalog.DREAM_BEGINNING_ID));
    }

    @Test
    void malformedEffectIdIsRejected() {
        StoryFlowNode node = new StoryFlowNode(
                "bad_effect",
                StoryEventType.NOTICE_READ,
                "opening.desert_town",
                "", "", StoryFlowScope.PLAYER,
                List.of(new StoryFlowEffect("record-clue")))
                .withCondition("cursor", "bad_effect");
        StoryFlowDefinition definition = new StoryFlowDefinition(
                "dreamingfishcore:flow/bad_effect",
                StoryStageCatalog.DREAM_BEGINNING_ID,
                StoryFlowScope.PLAYER,
                "bad_effect",
                List.of(node));

        assertThrows(IllegalStateException.class, () -> StoryFlowDefinitionStore.validate(
                new StoryFlowDefinitionDocument(List.of(definition)),
                Set.of(StoryStageCatalog.DREAM_BEGINNING_ID)));
    }

    @Test
    void effectInstanceIdMustBeStableAndUniqueWithinNode() {
        StoryFlowEffect first = new StoryFlowEffect("NOTIFY_PLAYER")
                .withId("same");
        StoryFlowEffect second = new StoryFlowEffect("SYNC_PLAYER")
                .withId("same");
        StoryFlowNode node = new StoryFlowNode(
                "duplicate_effect",
                StoryEventType.NOTICE_READ,
                "opening.desert_town",
                "", "", StoryFlowScope.PLAYER,
                List.of(first, second))
                .withCondition("cursor", "duplicate_effect");
        StoryFlowDefinition definition = new StoryFlowDefinition(
                "dreamingfishcore:flow/duplicate_effect",
                StoryStageCatalog.DREAM_BEGINNING_ID,
                StoryFlowScope.PLAYER,
                "duplicate_effect",
                List.of(node));

        assertThrows(IllegalStateException.class, () -> StoryFlowDefinitionStore.validate(
                new StoryFlowDefinitionDocument(List.of(definition)),
                Set.of(StoryStageCatalog.DREAM_BEGINNING_ID)));
    }
}
