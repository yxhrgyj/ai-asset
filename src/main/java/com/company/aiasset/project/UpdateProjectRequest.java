package com.company.aiasset.project;

public class UpdateProjectRequest {
    private String name;
    private String description;
    private String[] techStacks;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String[] getTechStacks() {
        return techStacks;
    }

    public void setTechStacks(String[] techStacks) {
        this.techStacks = techStacks;
    }
}
