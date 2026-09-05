package com.company.aiasset.project;

import com.company.aiasset.asset.AssetVersion;
import com.company.aiasset.asset.AssetVersionRepository;
import com.company.aiasset.security.AuthUtil;
import com.company.aiasset.rule.RuleMergeService;
import com.company.aiasset.team.TeamMemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 项目管理 API
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final ProjectRuleRepository projectRuleRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final RuleMergeService ruleMergeService;
    private final AuthUtil authUtil;
    private final AssetVersionRepository assetVersionRepository;

    public ProjectController(
            ProjectRepository projectRepository,
            ProjectRuleRepository projectRuleRepository,
            TeamMemberRepository teamMemberRepository,
            RuleMergeService ruleMergeService,
            AuthUtil authUtil,
            AssetVersionRepository assetVersionRepository
    ) {
        this.projectRepository = projectRepository;
        this.projectRuleRepository = projectRuleRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.ruleMergeService = ruleMergeService;
        this.authUtil = authUtil;
        this.assetVersionRepository = assetVersionRepository;
    }

    /**
     * 列出当前用户可见的项目
     */
    @GetMapping
    public ResponseEntity<List<ProjectDto>> listProjects() {
        var currentUser = authUtil.getCurrentUser();
        UUID userId = currentUser.getId();
        Map<UUID, Project> visibleProjects = new LinkedHashMap<>();

        projectRepository.findByOwnerUserIdAndArchivedFalseOrderByCreatedAtDesc(userId)
                .forEach(project -> visibleProjects.putIfAbsent(project.getId(), project));

        if (currentUser.getTeamId() != null) {
            projectRepository.findByTeamIdAndArchivedFalseOrderByCreatedAtDesc(currentUser.getTeamId())
                    .forEach(project -> visibleProjects.putIfAbsent(project.getId(), project));
        }

        List<ProjectDto> dtos = visibleProjects.values().stream()
                .filter(project -> !project.isArchived())
                .map(this::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * 创建项目
     */
    @PostMapping
    public ResponseEntity<ProjectDto> createProject(@RequestBody CreateProjectRequest request) {
        if (request == null || isBlank(request.getName()) || isBlank(request.getSlug())) {
            return ResponseEntity.badRequest().build();
        }

        UUID userId = authUtil.getCurrentUserId();
        String slug = request.getSlug().trim();

        if (projectRepository.existsBySlug(slug)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        Project project = new Project();
        project.setName(request.getName().trim());
        project.setSlug(slug);
        project.setDescription(request.getDescription());
        project.setOwnerUserId(userId);
        project.setTeamId(request.getTeamId());
        project.setTechStacks(request.getTechStacks() != null ? request.getTechStacks() : new String[0]);

        project = projectRepository.save(project);

        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(project));
    }

    /**
     * 获取项目详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProjectDto> getProject(@PathVariable UUID id) {
        Optional<Project> projectOpt = projectRepository.findById(id);

        if (projectOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Project project = projectOpt.get();

        // 权限检查：用户必须是项目拥有者或团队成员
        if (!canAccessProject(project)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(toDto(project));
    }

    /**
     * 更新项目
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProjectDto> updateProject(@PathVariable UUID id, @RequestBody UpdateProjectRequest request) {
        Optional<Project> projectOpt = projectRepository.findById(id);

        if (projectOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Project project = projectOpt.get();

        // 权限检查：仅项目拥有者可修改
        if (!project.getOwnerUserId().equals(authUtil.getCurrentUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (project.isArchived()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        if (request == null) {
            return ResponseEntity.badRequest().build();
        }

        if (request.getName() != null) {
            if (isBlank(request.getName())) {
                return ResponseEntity.badRequest().build();
            }
            project.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            project.setDescription(request.getDescription());
        }
        if (request.getTechStacks() != null) {
            project.setTechStacks(request.getTechStacks());
        }

        project = projectRepository.save(project);

        return ResponseEntity.ok(toDto(project));
    }

    /**
     * 归档项目
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archiveProject(@PathVariable UUID id) {
        Optional<Project> projectOpt = projectRepository.findById(id);

        if (projectOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Project project = projectOpt.get();

        if (!project.getOwnerUserId().equals(authUtil.getCurrentUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (project.isArchived()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        project.setArchived(true);
        projectRepository.save(project);

        return ResponseEntity.noContent().build();
    }

    /**
     * 获取项目绑定的规则列表
     */
    @GetMapping("/{id}/rules")
    public ResponseEntity<List<ProjectRuleDto>> getProjectRules(@PathVariable UUID id) {
        if (!projectRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        Optional<Project> projectOpt = projectRepository.findById(id);
        if (projectOpt.isPresent() && !canAccessProject(projectOpt.get())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<ProjectRule> rules = projectRuleRepository.findByProjectIdOrderByCreatedAt(id);

        List<ProjectRuleDto> dtos = rules.stream()
                .map(this::toRuleDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * 为项目添加规则
     */
    @PostMapping("/{id}/rules")
    public ResponseEntity<ProjectRuleDto> addProjectRule(@PathVariable UUID id, @RequestBody AddProjectRuleRequest request) {
        Optional<Project> projectOpt = projectRepository.findById(id);

        if (projectOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Project project = projectOpt.get();

        if (!project.getOwnerUserId().equals(authUtil.getCurrentUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (project.isArchived()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        if (request == null || request.getAssetVersionId() == null) {
            return ResponseEntity.badRequest().build();
        }

        Optional<AssetVersion> versionOpt = assetVersionRepository.findById(request.getAssetVersionId());
        if (versionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AssetVersion version = versionOpt.get();
        if (version.getStatus() != AssetVersion.Status.PUBLISHED
                && version.getStatus() != AssetVersion.Status.DEPRECATED) {
            return ResponseEntity.badRequest().build();
        }

        boolean assetAlreadyBound = projectRuleRepository.findByProjectIdOrderByCreatedAt(id).stream()
                .anyMatch(existing -> Objects.equals(existing.getAssetId(), version.getAssetId()));
        if (assetAlreadyBound) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        ProjectRule rule = new ProjectRule();
        rule.setProjectId(id);
        rule.setAssetVersionId(request.getAssetVersionId());
        rule.setAssetId(version.getAssetId());
        rule.setEnabled(true);
        rule.setAddedBy(authUtil.getCurrentUserId());

        rule = projectRuleRepository.save(rule);

        return ResponseEntity.status(HttpStatus.CREATED).body(toRuleDto(rule));
    }

    /**
     * 移除项目规则
     */
    @DeleteMapping("/{id}/rules/{assetVersionId}")
    @Transactional
    public ResponseEntity<Void> removeProjectRule(@PathVariable UUID id, @PathVariable UUID assetVersionId) {
        Optional<Project> projectOpt = projectRepository.findById(id);

        if (projectOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Project project = projectOpt.get();

        if (!project.getOwnerUserId().equals(authUtil.getCurrentUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (project.isArchived()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        projectRuleRepository.deleteByProjectIdAndAssetVersionId(id, assetVersionId);

        return ResponseEntity.noContent().build();
    }

    /**
     * 获取项目的合并规则
     */
    @GetMapping("/{id}/merged-rules")
    public ResponseEntity<MergedRulesDto> getMergedRules(@PathVariable UUID id) {
        if (!projectRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        Optional<Project> projectOpt = projectRepository.findById(id);
        if (projectOpt.isPresent() && !canAccessProject(projectOpt.get())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        RuleMergeService.MergedRulesResult result = ruleMergeService.mergeRulesForProject(id);

        MergedRulesDto dto = new MergedRulesDto();
        dto.setRules(result.getRules());
        dto.setOrgCount(result.getOrgCount());
        dto.setTechStackCount(result.getTechStackCount());
        dto.setProjectCount(result.getProjectCount());
        dto.setMergedCount(result.getMergedCount());

        return ResponseEntity.ok(dto);
    }

    /**
     * 下载项目合并规则为 Markdown 格式
     */
    @GetMapping("/{id}/merged-rules/markdown")
    public ResponseEntity<String> getMergedRulesMarkdown(@PathVariable UUID id) {
        if (!projectRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        Optional<Project> projectOpt = projectRepository.findById(id);
        if (projectOpt.isPresent() && !canAccessProject(projectOpt.get())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        RuleMergeService.MergedRulesResult result = ruleMergeService.mergeRulesForProject(id);
        String markdown = ruleMergeService.exportToMarkdown(result);

        return ResponseEntity.ok()
                .header("Content-Type", "text/markdown; charset=utf-8")
                .header("Content-Disposition", "attachment; filename=\"rules-" + id + ".md\"")
                .body(markdown);
    }

    // 权限检查
    private boolean canAccessProject(Project project) {
        var currentUser = authUtil.getCurrentUser();
        UUID userId = currentUser.getId();

        if (project.getOwnerUserId().equals(userId)) {
            return true;
        }

        return project.getTeamId() != null
                && project.getTeamId().equals(currentUser.getTeamId());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    // DTO 转换
    private ProjectDto toDto(Project project) {
        ProjectDto dto = new ProjectDto();
        dto.setId(project.getId());
        dto.setName(project.getName());
        dto.setSlug(project.getSlug());
        dto.setDescription(project.getDescription());
        dto.setOwnerUserId(project.getOwnerUserId());
        dto.setTeamId(project.getTeamId());
        dto.setTechStacks(project.getTechStacks());
        dto.setArchived(project.isArchived());
        dto.setCreatedAt(project.getCreatedAt());
        dto.setUpdatedAt(project.getUpdatedAt());
        return dto;
    }

    private ProjectRuleDto toRuleDto(ProjectRule rule) {
        ProjectRuleDto dto = new ProjectRuleDto();
        dto.setId(rule.getId());
        dto.setProjectId(rule.getProjectId());
        dto.setAssetId(rule.getAssetId());
        dto.setAssetVersionId(rule.getAssetVersionId());
        dto.setEnabled(rule.getEnabled());
        dto.setAddedBy(rule.getAddedBy());
        dto.setCreatedAt(rule.getCreatedAt());
        dto.setUpdatedAt(rule.getUpdatedAt());
        return dto;
    }
}
