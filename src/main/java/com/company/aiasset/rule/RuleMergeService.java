package com.company.aiasset.rule;

import com.company.aiasset.asset.Asset;
import com.company.aiasset.asset.AssetRepository;
import com.company.aiasset.asset.AssetVersion;
import com.company.aiasset.asset.AssetVersionRepository;
import com.company.aiasset.project.Project;
import com.company.aiasset.project.ProjectRepository;
import com.company.aiasset.project.ProjectRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 规则合并引擎。按优先级合并三层规则：组织级 < 技术栈级 < 项目级。
 * 重复的 rule_key 按优先级覆盖，最终输出合并后的规则列表。
 */
@Service
public class RuleMergeService {

    private final AssetRepository assetRepository;
    private final AssetVersionRepository assetVersionRepository;
    private final RuleItemRepository ruleItemRepository;
    private final ProjectRepository projectRepository;
    private final ProjectRuleRepository projectRuleRepository;

    public RuleMergeService(
            AssetRepository assetRepository,
            AssetVersionRepository assetVersionRepository,
            RuleItemRepository ruleItemRepository,
            ProjectRepository projectRepository,
            ProjectRuleRepository projectRuleRepository
    ) {
        this.assetRepository = assetRepository;
        this.assetVersionRepository = assetVersionRepository;
        this.ruleItemRepository = ruleItemRepository;
        this.projectRepository = projectRepository;
        this.projectRuleRepository = projectRuleRepository;
    }

    /**
     * 为项目合并规则
     */
    @Transactional(readOnly = true)
    public MergedRulesResult mergeRulesForProject(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        // 1. 收集组织级规则（全局 RULE 资产，scope=ORGANIZATION）
        List<RuleItem> orgRules = collectOrganizationRules();

        // 2. 收集技术栈级规则（匹配项目声明的 techStacks）
        List<RuleItem> techStackRules = collectTechStackRules(project.getTechStacks());

        // 3. 收集项目级规则（项目手工绑定的规则）
        List<RuleItem> projectRules = collectProjectRules(projectId);

        // 4. 合并：组织 < 技术栈 < 项目，按 rule_key 去重
        List<RuleItem> merged = merge(orgRules, techStackRules, projectRules);

        return new MergedRulesResult(merged, orgRules.size(), techStackRules.size(), projectRules.size());
    }

    /**
     * 收集组织级规则
     */
    private List<RuleItem> collectOrganizationRules() {
        List<Asset> orgAssets = assetRepository.findByTypeAndScopeAndArchivedFalse(
                Asset.Type.RULE,
                Asset.Scope.ORGANIZATION
        );

        // 获取每个资产的已发布版本
        List<UUID> publishedVersionIds = new ArrayList<>();
        for (Asset asset : orgAssets) {
            List<AssetVersion> versions = assetVersionRepository.findByAssetIdOrderByCreatedAtDesc(asset.getId());
            versions.stream()
                    .filter(v -> v.getStatus() == AssetVersion.Status.PUBLISHED)
                    .findFirst()
                    .ifPresent(v -> publishedVersionIds.add(v.getId()));
        }

        if (publishedVersionIds.isEmpty()) {
            return Collections.emptyList();
        }

        return ruleItemRepository.findByAssetVersionIds(publishedVersionIds);
    }

    /**
     * 收集技术栈级规则
     */
    private List<RuleItem> collectTechStackRules(String[] techStacks) {
        if (techStacks == null || techStacks.length == 0) {
            return Collections.emptyList();
        }

        List<Asset> techStackAssets = assetRepository.findByTypeAndScopeAndArchivedFalse(
                Asset.Type.RULE,
                Asset.Scope.TECH_STACK
        );

        // 筛选 tags 包含项目声明的任一技术栈的资产
        Set<String> projectTechSet = new HashSet<>(Arrays.asList(techStacks));
        List<UUID> matchedVersionIds = new ArrayList<>();

        for (Asset asset : techStackAssets) {
            String[] tags = asset.getTags();
            if (tags == null || tags.length == 0) {
                continue;
            }

            boolean matches = Arrays.stream(tags).anyMatch(projectTechSet::contains);
            if (matches) {
                List<AssetVersion> versions = assetVersionRepository.findByAssetIdOrderByCreatedAtDesc(asset.getId());
                versions.stream()
                        .filter(v -> v.getStatus() == AssetVersion.Status.PUBLISHED)
                        .findFirst()
                        .ifPresent(v -> matchedVersionIds.add(v.getId()));
            }
        }

        if (matchedVersionIds.isEmpty()) {
            return Collections.emptyList();
        }

        return ruleItemRepository.findByAssetVersionIds(matchedVersionIds);
    }

    /**
     * 收集项目级规则
     */
    private List<RuleItem> collectProjectRules(UUID projectId) {
        List<UUID> versionIds = projectRuleRepository.findAssetVersionIdsByProjectId(projectId);

        if (versionIds.isEmpty()) {
            return Collections.emptyList();
        }

        return ruleItemRepository.findByAssetVersionIds(versionIds);
    }

    /**
     * 合并规则：按优先级覆盖相同 rule_key
     */
    private List<RuleItem> merge(List<RuleItem> org, List<RuleItem> techStack, List<RuleItem> project) {
        Map<String, RuleItem> ruleMap = new LinkedHashMap<>();

        // 优先级从低到高：组织 < 技术栈 < 项目
        for (RuleItem rule : org) {
            ruleMap.put(rule.getRuleKey(), rule);
        }

        for (RuleItem rule : techStack) {
            ruleMap.put(rule.getRuleKey(), rule);
        }

        for (RuleItem rule : project) {
            ruleMap.put(rule.getRuleKey(), rule);
        }

        return new ArrayList<>(ruleMap.values());
    }

    /**
     * 导出为 Markdown 格式
     */
    public String exportToMarkdown(MergedRulesResult result) {
        StringBuilder md = new StringBuilder();

        md.append("# 合并后的规则\n\n");
        md.append("规则统计：\n");
        md.append("- 组织级规则：").append(result.getOrgCount()).append(" 条\n");
        md.append("- 技术栈级规则：").append(result.getTechStackCount()).append(" 条\n");
        md.append("- 项目级规则：").append(result.getProjectCount()).append(" 条\n");
        md.append("- **合并后规则：").append(result.getMergedCount()).append(" 条**\n\n");
        md.append("---\n\n");

        for (RuleItem rule : result.getRules()) {
            md.append("## ").append(rule.getTitle()).append("\n\n");
            md.append("**规则键：** `").append(rule.getRuleKey()).append("`\n\n");
            md.append("**级别：** ").append(rule.getLevel()).append("\n\n");

            if (rule.getPathGlobs() != null && rule.getPathGlobs().length > 0) {
                md.append("**适用路径：**\n");
                for (String glob : rule.getPathGlobs()) {
                    md.append("- `").append(glob).append("`\n");
                }
                md.append("\n");
            }

            md.append("**规则内容：**\n\n");
            md.append(rule.getBody()).append("\n\n");
            md.append("---\n\n");
        }

        return md.toString();
    }

    /**
     * 合并结果
     */
    public static class MergedRulesResult {
        private final List<RuleItem> rules;
        private final int orgCount;
        private final int techStackCount;
        private final int projectCount;

        public MergedRulesResult(List<RuleItem> rules, int orgCount, int techStackCount, int projectCount) {
            this.rules = rules;
            this.orgCount = orgCount;
            this.techStackCount = techStackCount;
            this.projectCount = projectCount;
        }

        public List<RuleItem> getRules() {
            return rules;
        }

        public int getOrgCount() {
            return orgCount;
        }

        public int getTechStackCount() {
            return techStackCount;
        }

        public int getProjectCount() {
            return projectCount;
        }

        public int getMergedCount() {
            return rules.size();
        }
    }
}
