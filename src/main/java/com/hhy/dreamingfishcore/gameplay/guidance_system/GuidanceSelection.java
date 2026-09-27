package com.hhy.dreamingfishcore.gameplay.guidance_system;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 纯客户端的追踪选择；修改它不会改变任何任务完成事实。 */
public final class GuidanceSelection {
    private List<GuidanceViewData> active = List.of();
    private String selectedId = "";
    private GuidanceViewData selected;

    public void update(List<GuidanceViewData> entries) {
        active = entries.stream().filter(Objects::nonNull)
                .filter(entry -> entry.status() == GuidanceEntry.Status.ACTIVE)
                .sorted(Comparator.comparingLong(GuidanceViewData::createdAtEpochMillis)
                        .thenComparing(GuidanceViewData::definitionId)).toList();
        selected = active.stream().filter(entry -> entry.definitionId().equals(selectedId))
                .findFirst().orElse(active.isEmpty() ? null : active.getFirst());
        selectedId = selected == null ? "" : selected.definitionId();
    }

    public List<GuidanceViewData> active() { return active; }

    public GuidanceViewData selected() {
        // Read by the HUD at the display frame rate; resolve only on mutation.
        return selected;
    }

    public boolean select(String definitionId) {
        for (GuidanceViewData entry : active) {
            if (entry.definitionId().equals(definitionId)) {
                selectedId = definitionId;
                selected = entry;
                return true;
            }
        }
        return false;
    }

    public void cycle(int direction) {
        if (active.size() > 1) {
            int index = active.indexOf(selected);
            selected = active.get(Math.floorMod(index + Integer.signum(direction), active.size()));
            selectedId = selected.definitionId();
        }
    }

    public void clear() { active = List.of(); selectedId = ""; selected = null; }
}
