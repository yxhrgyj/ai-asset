package com.company.aiasset.project;

import java.util.UUID;

public class CreateProjectRequest {
    private String name;
    private String slug;
    private String description;
    private UUID teamId;
    private String[] techStacks;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public void setTeamId(UUID teamId) {
        this.teamId = teamId;
    }

    public String[] getTechStacks() {
        return techStacks;
    }

    public void setTechStacks(String[] techStacks) {
        this.techStacks = techStacks;
    }
}
