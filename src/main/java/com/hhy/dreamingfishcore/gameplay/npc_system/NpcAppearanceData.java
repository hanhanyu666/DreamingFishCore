package com.hhy.dreamingfishcore.gameplay.npc_system;

/**
 * NPC 的纯外观配置。它由服务端从 npc_data.json 读取，随后由 StoryNpcEntity
 * 同步给客户端；客户端渲染器不会直接读取服务器配置文件。
 */
public class NpcAppearanceData {
    private String skin = "";
    private String model = "wide";
    private boolean showName = true;

    public String getSkin() {
        return skin == null ? "" : skin;
    }

    public void setSkin(String skin) {
        this.skin = skin;
    }

    public String getModel() {
        return "slim".equalsIgnoreCase(model) ? "slim" : "wide";
    }

    public void setModel(String model) {
        this.model = model;
    }

    public boolean isShowName() {
        return showName;
    }

    public void setShowName(boolean showName) {
        this.showName = showName;
    }
}
