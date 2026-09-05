package com.company.aiasset.bootstrap;

import com.company.aiasset.asset.Asset;
import com.company.aiasset.asset.AssetRepository;
import com.company.aiasset.asset.AssetVersion;
import com.company.aiasset.asset.AssetVersionRepository;
import com.company.aiasset.project.Project;
import com.company.aiasset.project.ProjectRepository;
import com.company.aiasset.project.ProjectRule;
import com.company.aiasset.project.ProjectRuleRepository;
import com.company.aiasset.rule.RuleItem;
import com.company.aiasset.rule.RuleItemRepository;
import com.company.aiasset.user.User;
import com.company.aiasset.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/test-data")
@Profile("test-data")
public class TestDataController {

    private final AssetRepository assetRepo;
    private final AssetVersionRepository versionRepo;
    private final RuleItemRepository ruleItemRepo;
    private final ProjectRepository projectRepo;
    private final ProjectRuleRepository projectRuleRepo;
    private final UserRepository userRepo;
    private final EntityManager entityManager;

    public TestDataController(AssetRepository assetRepo,
                            AssetVersionRepository versionRepo,
                            RuleItemRepository ruleItemRepo,
                            ProjectRepository projectRepo,
                            ProjectRuleRepository projectRuleRepo,
                            UserRepository userRepo,
                            EntityManager entityManager) {
        this.assetRepo = assetRepo;
        this.versionRepo = versionRepo;
        this.ruleItemRepo = ruleItemRepo;
        this.projectRepo = projectRepo;
        this.projectRuleRepo = projectRuleRepo;
        this.userRepo = userRepo;
        this.entityManager = entityManager;
    }

    @PostMapping("/clean")
    @Transactional
    public String cleanTestData() {
        projectRuleRepo.deleteAll();
        projectRepo.deleteAll();
        ruleItemRepo.deleteAll();
        versionRepo.deleteAll();
        assetRepo.deleteAll();
        return "Test data cleaned";
    }

    @PostMapping("/import")
    @Transactional
    public String importTestData() {
        User admin = userRepo.findByUsernameIgnoreCase("admin")
                .orElseThrow(() -> new RuntimeException("Admin user not found"));

        // 1. Organization-level rule asset
        Asset orgAsset = new Asset();
        orgAsset.setName("通用编码规范");
        orgAsset.setSlug("org-coding-standards");
        orgAsset.setSummary("适用于所有项目的通用编码规范");
        orgAsset.setType(Asset.Type.RULE);
        orgAsset.setScope(Asset.Scope.ORGANIZATION);
        orgAsset.setOwnerUserId(admin.getId());
        orgAsset = assetRepo.save(orgAsset);

        AssetVersion orgVersion = new AssetVersion();
        orgVersion.setAssetId(orgAsset.getId());
        orgVersion.setVersionNo(1);
        orgVersion.setStatus(AssetVersion.Status.DRAFT);
        orgVersion.setContentHash("hash-org-1");
        orgVersion.setCreatedBy(admin.getId());
        orgVersion = versionRepo.save(orgVersion);

        RuleItem orgRule1 = new RuleItem();
        orgRule1.setAssetVersionId(orgVersion.getId());
        orgRule1.setRuleKey("coding.naming-convention");
        orgRule1.setTitle("命名规范");
        orgRule1.setBody("所有变量和函数名使用驼峰命名法（camelCase）。类名使用帕斯卡命名法（PascalCase）。");
        orgRule1.setLevel(RuleItem.RuleLevel.REQUIRED);
        orgRule1.setPathGlobs(new String[]{"**/*.java", "**/*.ts", "**/*.js"});
        orgRule1.setSortOrder(1);
        ruleItemRepo.save(orgRule1);

        RuleItem orgRule2 = new RuleItem();
        orgRule2.setAssetVersionId(orgVersion.getId());
        orgRule2.setRuleKey("coding.comments");
        orgRule2.setTitle("代码注释");
        orgRule2.setBody("复杂逻辑必须添加注释说明。公共API必须有完整的文档注释。");
        orgRule2.setLevel(RuleItem.RuleLevel.REQUIRED);
        orgRule2.setPathGlobs(new String[]{"**/*"});
        orgRule2.setSortOrder(2);
        ruleItemRepo.save(orgRule2);

        orgVersion.setStatus(AssetVersion.Status.PUBLISHED);
        orgVersion.setPublishedAt(Instant.now());
        versionRepo.save(orgVersion);
        entityManager.flush();

        // 2. Java tech-stack rule asset
        Asset javaAsset = new Asset();
        javaAsset.setName("Java开发规范");
        javaAsset.setSlug("java-standards");
        javaAsset.setSummary("Java项目开发规范");
        javaAsset.setType(Asset.Type.RULE);
        javaAsset.setScope(Asset.Scope.TECH_STACK);
        javaAsset.setTechStack("Java");
        javaAsset.setTags(new String[]{"Java", "Spring Boot"});
        javaAsset.setOwnerUserId(admin.getId());
        javaAsset = assetRepo.save(javaAsset);

        AssetVersion javaVersion = new AssetVersion();
        javaVersion.setAssetId(javaAsset.getId());
        javaVersion.setVersionNo(1);
        javaVersion.setStatus(AssetVersion.Status.DRAFT);
        javaVersion.setContentHash("hash-java-1");
        javaVersion.setCreatedBy(admin.getId());
        javaVersion = versionRepo.save(javaVersion);

        RuleItem javaRule1 = new RuleItem();
        javaRule1.setAssetVersionId(javaVersion.getId());
        javaRule1.setRuleKey("java.package-structure");
        javaRule1.setTitle("Java包结构");
        javaRule1.setBody("按功能模块划分包结构，如 com.company.module.feature。");
        javaRule1.setLevel(RuleItem.RuleLevel.REQUIRED);
        javaRule1.setPathGlobs(new String[]{"**/*.java"});
        javaRule1.setSortOrder(1);
        ruleItemRepo.save(javaRule1);

        RuleItem javaRule2 = new RuleItem();
        javaRule2.setAssetVersionId(javaVersion.getId());
        javaRule2.setRuleKey("coding.naming-convention");
        javaRule2.setTitle("Java命名规范（覆盖组织级）");
        javaRule2.setBody("变量和方法使用驼峰命名。类名使用帕斯卡命名。常量全大写下划线分隔。");
        javaRule2.setLevel(RuleItem.RuleLevel.REQUIRED);
        javaRule2.setPathGlobs(new String[]{"**/*.java"});
        javaRule2.setSortOrder(2);
        ruleItemRepo.save(javaRule2);

        javaVersion.setStatus(AssetVersion.Status.PUBLISHED);
        javaVersion.setPublishedAt(Instant.now());
        versionRepo.save(javaVersion);
        entityManager.flush();

        // 3. Vue tech-stack rule asset
        Asset vueAsset = new Asset();
        vueAsset.setName("Vue开发规范");
        vueAsset.setSlug("vue-standards");
        vueAsset.setSummary("Vue.js项目开发规范");
        vueAsset.setType(Asset.Type.RULE);
        vueAsset.setScope(Asset.Scope.TECH_STACK);
        vueAsset.setTechStack("Vue");
        vueAsset.setTags(new String[]{"Vue", "TypeScript"});
        vueAsset.setOwnerUserId(admin.getId());
        vueAsset = assetRepo.save(vueAsset);

        AssetVersion vueVersion = new AssetVersion();
        vueVersion.setAssetId(vueAsset.getId());
        vueVersion.setVersionNo(1);
        vueVersion.setStatus(AssetVersion.Status.DRAFT);
        vueVersion.setContentHash("hash-vue-1");
        vueVersion.setCreatedBy(admin.getId());
        vueVersion = versionRepo.save(vueVersion);

        RuleItem vueRule1 = new RuleItem();
        vueRule1.setAssetVersionId(vueVersion.getId());
        vueRule1.setRuleKey("vue.component-naming");
        vueRule1.setTitle("Vue组件命名");
        vueRule1.setBody("组件文件使用PascalCase命名。单文件组件必须包含template、script、style三部分。");
        vueRule1.setLevel(RuleItem.RuleLevel.REQUIRED);
        vueRule1.setPathGlobs(new String[]{"**/*.vue"});
        vueRule1.setSortOrder(1);
        ruleItemRepo.save(vueRule1);

        RuleItem vueRule2 = new RuleItem();
        vueRule2.setAssetVersionId(vueVersion.getId());
        vueRule2.setRuleKey("vue.composition-api");
        vueRule2.setTitle("Composition API使用");
        vueRule2.setBody("优先使用Composition API。setup语法糖是推荐写法。");
        vueRule2.setLevel(RuleItem.RuleLevel.RECOMMENDED);
        vueRule2.setPathGlobs(new String[]{"**/*.vue"});
        vueRule2.setSortOrder(2);
        ruleItemRepo.save(vueRule2);

        vueVersion.setStatus(AssetVersion.Status.PUBLISHED);
        vueVersion.setPublishedAt(Instant.now());
        versionRepo.save(vueVersion);
        entityManager.flush();

        // 4. Test project
        Project project = new Project();
        project.setName("AI资产管理平台");
        project.setSlug("ai-asset-platform");
        project.setDescription("本项目的测试数据");
        project.setOwnerUserId(admin.getId());
        project.setTechStacks(new String[]{"Java", "Spring Boot", "Vue", "TypeScript"});
        project = projectRepo.save(project);

        // 5. Project-level rule asset
        Asset projAsset = new Asset();
        projAsset.setName("AI平台特定规范");
        projAsset.setSlug("ai-platform-rules");
        projAsset.setSummary("本项目的特定开发规范");
        projAsset.setType(Asset.Type.RULE);
        projAsset.setScope(Asset.Scope.PROJECT);
        projAsset.setOwnerUserId(admin.getId());
        projAsset = assetRepo.save(projAsset);

        AssetVersion projVersion = new AssetVersion();
        projVersion.setAssetId(projAsset.getId());
        projVersion.setVersionNo(1);
        projVersion.setStatus(AssetVersion.Status.DRAFT);
        projVersion.setContentHash("hash-proj-1");
        projVersion.setCreatedBy(admin.getId());
        projVersion = versionRepo.save(projVersion);

        RuleItem projRule1 = new RuleItem();
        projRule1.setAssetVersionId(projVersion.getId());
        projRule1.setRuleKey("project.api-versioning");
        projRule1.setTitle("API版本管理");
        projRule1.setBody("所有REST API必须在路径中包含版本号，如 /api/v1/assets。");
        projRule1.setLevel(RuleItem.RuleLevel.REQUIRED);
        projRule1.setPathGlobs(new String[]{"**/controller/**/*.java"});
        projRule1.setSortOrder(1);
        ruleItemRepo.save(projRule1);

        RuleItem projRule2 = new RuleItem();
        projRule2.setAssetVersionId(projVersion.getId());
        projRule2.setRuleKey("coding.comments");
        projRule2.setTitle("注释规范（覆盖组织级）");
        projRule2.setBody("本项目代码注释使用中文。公共API必须包含完整的JavaDoc/TSDoc。");
        projRule2.setLevel(RuleItem.RuleLevel.REQUIRED);
        projRule2.setPathGlobs(new String[]{"**/*"});
        projRule2.setSortOrder(2);
        ruleItemRepo.save(projRule2);

        projVersion.setStatus(AssetVersion.Status.PUBLISHED);
        projVersion.setPublishedAt(Instant.now());
        versionRepo.save(projVersion);
        entityManager.flush();

        // 6. Link project-level rule to project
        ProjectRule projectRule = new ProjectRule();
        projectRule.setProjectId(project.getId());
        projectRule.setAssetId(projAsset.getId());
        projectRule.setAssetVersionId(projVersion.getId());
        projectRule.setEnabled(true);
        projectRule.setAddedBy(admin.getId());
        projectRuleRepo.save(projectRule);
        projectRuleRepo.save(projectRule);

        return "Test data imported successfully";
    }
}
