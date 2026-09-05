package com.company.aiasset.project;

import com.company.aiasset.asset.AssetVersion;
import com.company.aiasset.asset.AssetVersionRepository;
import com.company.aiasset.rule.RuleMergeService;
import com.company.aiasset.security.AuthUtil;
import com.company.aiasset.team.TeamMemberRepository;
import com.company.aiasset.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectControllerTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectRuleRepository projectRuleRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private RuleMergeService ruleMergeService;
    @Mock
    private AuthUtil authUtil;
    @Mock
    private AssetVersionRepository assetVersionRepository;

    private ProjectController controller;
    private UUID currentUserId;
    private UUID currentTeamId;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        currentTeamId = UUID.randomUUID();

        User currentUser = mock(User.class);
        lenient().when(currentUser.getId()).thenReturn(currentUserId);
        lenient().when(currentUser.getTeamId()).thenReturn(currentTeamId);
        lenient().when(authUtil.getCurrentUser()).thenReturn(currentUser);
        lenient().when(authUtil.getCurrentUserId()).thenReturn(currentUserId);
        lenient().when(projectRepository.save(any(Project.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(projectRuleRepository.save(any(ProjectRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        controller = new ProjectController(
                projectRepository,
                projectRuleRepository,
                teamMemberRepository,
                ruleMergeService,
                authUtil,
                assetVersionRepository
        );
    }

    @Test
    void listProjectsReturnsUnarchivedOwnedAndTeamProjectsWithoutDuplicates() {
        Project owned = project(UUID.randomUUID(), currentUserId, null, false);
        Project shared = project(UUID.randomUUID(), currentUserId, currentTeamId, false);
        Project teamProject = project(UUID.randomUUID(), UUID.randomUUID(), currentTeamId, false);

        when(projectRepository.findByOwnerUserIdAndArchivedFalseOrderByCreatedAtDesc(currentUserId))
                .thenReturn(List.of(owned, shared));
        when(projectRepository.findByTeamIdAndArchivedFalseOrderByCreatedAtDesc(currentTeamId))
                .thenReturn(List.of(shared, teamProject));

        ResponseEntity<List<ProjectDto>> response = controller.listProjects();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .extracting(ProjectDto::getId)
                .containsExactlyInAnyOrder(owned.getId(), shared.getId(), teamProject.getId());
    }

    @Test
    void getProjectAllowsAUserWhoseUsersTableTeamMatchesTheProject() {
        Project teamProject = project(UUID.randomUUID(), UUID.randomUUID(), currentTeamId, false);
        when(projectRepository.findById(teamProject.getId())).thenReturn(Optional.of(teamProject));

        ResponseEntity<ProjectDto> response = controller.getProject(teamProject.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verifyNoInteractions(teamMemberRepository);
    }

    @Test
    void createProjectRejectsBlankRequiredFields() {
        CreateProjectRequest request = new CreateProjectRequest();
        request.setName(" ");
        request.setSlug("");
        request.setTechStacks(new String[0]);

        ResponseEntity<ProjectDto> response = controller.createProject(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void updateProjectRejectsAnArchivedProject() {
        Project archived = project(UUID.randomUUID(), currentUserId, null, true);
        when(projectRepository.findById(archived.getId())).thenReturn(Optional.of(archived));
        UpdateProjectRequest request = new UpdateProjectRequest();
        request.setName("new name");

        ResponseEntity<ProjectDto> response = controller.updateProject(archived.getId(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void archiveProjectRejectsAnAlreadyArchivedProject() {
        Project archived = project(UUID.randomUUID(), currentUserId, null, true);
        when(projectRepository.findById(archived.getId())).thenReturn(Optional.of(archived));

        ResponseEntity<Void> response = controller.archiveProject(archived.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void addProjectRuleRejectsAnArchivedProject() {
        Project archived = project(UUID.randomUUID(), currentUserId, null, true);
        when(projectRepository.findById(archived.getId())).thenReturn(Optional.of(archived));
        AddProjectRuleRequest request = ruleRequest(UUID.randomUUID());

        ResponseEntity<ProjectRuleDto> response = controller.addProjectRule(archived.getId(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verifyNoInteractions(assetVersionRepository);
        verify(projectRuleRepository, never()).save(any(ProjectRule.class));
    }

    @Test
    void removeProjectRuleRejectsAnArchivedProject() {
        Project archived = project(UUID.randomUUID(), currentUserId, null, true);
        UUID versionId = UUID.randomUUID();
        when(projectRepository.findById(archived.getId())).thenReturn(Optional.of(archived));

        ResponseEntity<Void> response = controller.removeProjectRule(archived.getId(), versionId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(projectRuleRepository, never())
                .deleteByProjectIdAndAssetVersionId(archived.getId(), versionId);
    }

    @Test
    void addProjectRuleReturnsNotFoundForAMissingAssetVersion() {
        Project project = project(UUID.randomUUID(), currentUserId, null, false);
        UUID missingVersionId = UUID.randomUUID();
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(assetVersionRepository.findById(missingVersionId)).thenReturn(Optional.empty());

        ResponseEntity<ProjectRuleDto> response =
                controller.addProjectRule(project.getId(), ruleRequest(missingVersionId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(projectRuleRepository, never()).save(any(ProjectRule.class));
    }

    @Test
    void addProjectRuleRejectsADraftAssetVersion() {
        Project project = project(UUID.randomUUID(), currentUserId, null, false);
        UUID versionId = UUID.randomUUID();
        AssetVersion draft = version(UUID.randomUUID(), AssetVersion.Status.DRAFT);
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(assetVersionRepository.findById(versionId)).thenReturn(Optional.of(draft));

        ResponseEntity<ProjectRuleDto> response =
                controller.addProjectRule(project.getId(), ruleRequest(versionId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(projectRuleRepository, never()).save(any(ProjectRule.class));
    }

    @Test
    void addProjectRuleRejectsAnotherVersionOfAnAlreadyBoundAsset() {
        Project project = project(UUID.randomUUID(), currentUserId, null, false);
        UUID assetId = UUID.randomUUID();
        UUID requestedVersionId = UUID.randomUUID();
        AssetVersion published = version(assetId, AssetVersion.Status.PUBLISHED);
        ProjectRule existing = new ProjectRule();
        existing.setAssetId(assetId);

        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(assetVersionRepository.findById(requestedVersionId)).thenReturn(Optional.of(published));
        when(projectRuleRepository.findByProjectIdOrderByCreatedAt(project.getId()))
                .thenReturn(List.of(existing));

        ResponseEntity<ProjectRuleDto> response =
                controller.addProjectRule(project.getId(), ruleRequest(requestedVersionId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(projectRuleRepository, never()).save(any(ProjectRule.class));
    }

    private static Project project(
            UUID id,
            UUID ownerUserId,
            UUID teamId,
            boolean archived
    ) {
        Project project = new Project();
        project.setId(id);
        project.setName("Project " + id);
        project.setSlug("project-" + id);
        project.setOwnerUserId(ownerUserId);
        project.setTeamId(teamId);
        project.setTechStacks(new String[0]);
        project.setArchived(archived);
        project.setCreatedAt(Instant.now());
        project.setUpdatedAt(Instant.now());
        return project;
    }

    private static AddProjectRuleRequest ruleRequest(UUID assetVersionId) {
        AddProjectRuleRequest request = new AddProjectRuleRequest();
        request.setAssetVersionId(assetVersionId);
        return request;
    }

    private static AssetVersion version(UUID assetId, AssetVersion.Status status) {
        AssetVersion version = mock(AssetVersion.class);
        lenient().when(version.getAssetId()).thenReturn(assetId);
        when(version.getStatus()).thenReturn(status);
        return version;
    }
}
