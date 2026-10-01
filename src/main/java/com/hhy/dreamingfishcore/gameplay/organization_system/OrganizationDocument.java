package com.hhy.dreamingfishcore.gameplay.organization_system;

import java.util.ArrayList;
import java.util.List;

/** 组织世界存档的顶层容器（{@code data/dreamingfishcore/organizations.json}）。 */
public final class OrganizationDocument {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private List<Organization> organizations = new ArrayList<>();

    public OrganizationDocument() {
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public List<Organization> getOrganizations() {
        if (organizations == null) {
            organizations = new ArrayList<>();
        }
        return organizations;
    }

    public void setOrganizations(List<Organization> value) {
        this.organizations = value == null ? new ArrayList<>() : value;
    }
}
