package com.company.aiasset.project;

import com.company.aiasset.rule.RuleItem;

import java.util.List;

public class MergedRulesDto {
    private List<RuleItem> rules;
    private int orgCount;
    private int techStackCount;
    private int projectCount;
    private int mergedCount;

    public List<RuleItem> getRules() {
        return rules;
    }

    public void setRules(List<RuleItem> rules) {
        this.rules = rules;
    }

    public int getOrgCount() {
        return orgCount;
    }

    public void setOrgCount(int orgCount) {
        this.orgCount = orgCount;
    }

    public int getTechStackCount() {
        return techStackCount;
    }

    public void setTechStackCount(int techStackCount) {
        this.techStackCount = techStackCount;
    }

    public int getProjectCount() {
        return projectCount;
    }

    public void setProjectCount(int projectCount) {
        this.projectCount = projectCount;
    }

    public int getMergedCount() {
        return mergedCount;
    }

    public void setMergedCount(int mergedCount) {
        this.mergedCount = mergedCount;
    }
}
