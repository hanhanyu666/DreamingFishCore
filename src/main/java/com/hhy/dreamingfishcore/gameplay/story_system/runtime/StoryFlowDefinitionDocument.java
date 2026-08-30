package com.hhy.dreamingfishcore.gameplay.story_system.runtime;

import java.util.ArrayList;
import java.util.List;

/** config/dreamingfishcore/story_flows.json 的根对象。 */
public final class StoryFlowDefinitionDocument {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private List<StoryFlowDefinition> flows = new ArrayList<>();

    public StoryFlowDefinitionDocument() {
    }

    public StoryFlowDefinitionDocument(List<StoryFlowDefinition> flows) {
        this.flows = flows == null ? new ArrayList<>() : new ArrayList<>(flows);
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public List<StoryFlowDefinition> getFlows() {
        if (flows == null) {
            flows = new ArrayList<>();
        }
        return flows;
    }
}
