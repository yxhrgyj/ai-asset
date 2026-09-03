package com.company.aiasset.approval;

import com.company.aiasset.asset.Asset;
import com.company.aiasset.asset.AssetRepository;
import com.company.aiasset.asset.AssetVersion;
import com.company.aiasset.asset.AssetVersionRepository;
import com.company.aiasset.security.CurrentUser;
import com.company.aiasset.user.User;
import com.company.aiasset.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService service;
    private final ApprovalRepository approvals;
    private final AssetRepository assets;
    private final AssetVersionRepository versions;
    private final UserRepository users;

    public ApprovalController(ApprovalService service,
                             ApprovalRepository approvals,
                             AssetRepository assets,
                             AssetVersionRepository versions,
                             UserRepository users) {
        this.service = service;
        this.approvals = approvals;
        this.assets = assets;
        this.versions = versions;
        this.users = users;
    }

    /** 获取待审批列表 */
    @GetMapping("/pending")
    public ResponseEntity<List<ApprovalDto.Item>> getPending() {
        List<Approval> pending = service.getPendingApprovals();
        return ResponseEntity.ok(pending.stream().map(this::toItem).toList());
    }

    /** 获取某个版本的审批历史 */
    @GetMapping("/version/{versionId}")
    public ResponseEntity<List<ApprovalDto.Item>> getVersionApprovals(@PathVariable UUID versionId) {
        List<Approval> history = service.getVersionApprovals(versionId);
        return ResponseEntity.ok(history.stream().map(this::toItem).toList());
    }

    /** 提交审批 */
    @PostMapping("/submit/{assetId}")
    public ResponseEntity<ApprovalDto.Item> submit(@PathVariable UUID assetId, CurrentUser current) {
        Approval approval = service.submitForApproval(assetId, current);
        return ResponseEntity.status(201).body(toItem(approval));
    }

    /** 审批决定 */
    @PostMapping("/{approvalId}/decide")
    public ResponseEntity<ApprovalDto.Item> decide(@PathVariable UUID approvalId,
                                                   @RequestBody DecideRequest req,
                                                   CurrentUser current) {
        Approval approval = service.decide(approvalId, req.decision, req.comment, current);
        return ResponseEntity.ok(toItem(approval));
    }

    /** 撤回审批 */
    @PostMapping("/{approvalId}/withdraw")
    public ResponseEntity<Void> withdraw(@PathVariable UUID approvalId, CurrentUser current) {
        service.withdraw(approvalId, current);
        return ResponseEntity.noContent().build();
    }

    private ApprovalDto.Item toItem(Approval a) {
        AssetVersion version = versions.findById(a.getAssetVersionId()).orElse(null);
        Asset asset = version != null ? assets.findById(version.getAssetId()).orElse(null) : null;
        User submitter = users.findById(a.getSubmittedBy()).orElse(null);
        User decider = a.getDecidedBy() != null ? users.findById(a.getDecidedBy()).orElse(null) : null;

        return new ApprovalDto.Item(
                a.getId(),
                a.getAssetVersionId(),
                asset != null ? asset.getId() : null,
                asset != null ? asset.getName() : null,
                version != null ? version.getVersionNo() : 0,
                a.getSubmittedBy(),
                submitter != null ? submitter.getUsername() : null,
                a.getSubmittedAt(),
                a.getDecidedBy(),
                decider != null ? decider.getUsername() : null,
                a.getDecidedAt(),
                a.getDecision() != null ? a.getDecision().name() : null,
                a.getComment()
        );
    }

    public record DecideRequest(Approval.Decision decision, String comment) {}
}
