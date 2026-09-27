package com.hhy.dreamingfishcore.gameplay.hospital_system;

/** 医院世界事实。个人阅读、复查及建设结果仍使用 StoryWorldState 的任务记录。 */
public final class HospitalProgress {
    private boolean started;
    private String locationId = "";

    public boolean start() {
        if (started) return false;
        started = true;
        return true;
    }

    public boolean isStarted() { return started; }
    public String getLocationId() { return locationId == null ? "" : locationId; }

    public boolean setLocationId(String value) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException("医院地点 ID 非法");
        }
        if (value.equals(locationId)) return false;
        locationId = value;
        return true;
    }

    public void validate() {
        if (locationId == null || locationId.length() > 128) {
            throw new IllegalStateException("医院地点记录非法");
        }
    }
}
