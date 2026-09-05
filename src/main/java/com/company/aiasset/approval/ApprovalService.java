package com.company.aiasset.approval;

import com.company.aiasset.asset.Asset;
import com.company.aiasset.asset.AssetRepository;
import com.company.aiasset.asset.AssetVersion;
import com.company.aiasset.asset.AssetVersionRepository;
import com.company.aiasset.security.CurrentUser;
import com.company.aiasset.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class ApprovalService {

    private final ApprovalRepository approvals;
    private final AssetRepository assets;
    private final AssetVersionRepository versions;

    public ApprovalService(ApprovalRepository approvals,
                          AssetRepository assets,
                          AssetVersionRepository versions) {
        this.approvals = approvals;
        this.assets = assets;
        this.versions = versions;
    }

    /**
     * 提交审批。将草稿状态改为 PENDING，创建审批记录，冻结内容哈希。
     */
    @Transactional
    public Approval submitForApproval(UUID assetId, CurrentUser current) {
        Asset asset = assets.findById(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "资产不存在"));

        // 只有资产负责人或管理员可以提交
        if (!asset.getOwnerUserId().equals(current.user().getId())
                && current.user().getRole() != User.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有资产负责人或管理员可以提交审批");
        }

        AssetVersion version = versions.findOpenVersion(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "没有待提交的草稿"));

        if (version.getStatus() != AssetVersion.Status.DRAFT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "版本状态为 " + version.getStatus() + "，不能提交审批");
        }

        if (version.getBody() == null || version.getBody().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "正文为空，不能提交审批");
        }

        // 检查是否已有待审批记录
        if (approvals.findPendingByVersion(version.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该版本已在审批中");
        }

        // 计算内容哈希并冻结
        String hash = sha256(version.getBody());
        version.setStatus(AssetVersion.Status.PENDING);
        version.setContentHash(hash);

        Approval approval = new Approval();
        approval.setAssetVersionId(version.getId());
        approval.setSubmittedBy(current.user().getId());
        approval.setFrozenHash(hash);

        return approvals.save(approval);
    }

    /**
     * 审批操作：批准或驳回。
     */
    @Transactional
    public Approval decide(UUID approvalId, Approval.Decision decision, String comment, CurrentUser current) {
        // 需要审批人权限
        if (current.user().getRole() != User.Role.APPROVER
                && current.user().getRole() != User.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要审批人权限");
        }

        Approval approval = approvals.findById(approvalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "审批记录不存在"));

        if (!approval.isPending()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该审批已处理");
        }

        // 审批人不能是提交人
        if (approval.getSubmittedBy().equals(current.user().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能审批自己提交的内容");
        }

        AssetVersion version = versions.findById(approval.getAssetVersionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "版本不存在"));

        // 检查内容是否在审批期间被修改
        String currentHash = sha256(version.getBody());
        if (!currentHash.equals(approval.getFrozenHash())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "内容在审批期间被修改，请重新提交审批");
        }

        approval.setDecidedBy(current.user().getId());
        approval.setDecidedAt(Instant.now());
        approval.setDecision(decision);
        approval.setComment(comment);

        // 更新版本状态
        if (decision == Approval.Decision.APPROVED) {
            version.setStatus(AssetVersion.Status.PUBLISHED);
            version.setPublishedAt(Instant.now());
        } else {
            version.setStatus(AssetVersion.Status.REJECTED);
        }

        return approval;
    }

    /**
     * 撤回审批。只有提交人可以撤回，且只能撤回待审批状态的。
     */
    @Transactional
    public void withdraw(UUID approvalId, CurrentUser current) {
        Approval approval = approvals.findById(approvalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "审批记录不存在"));

        if (!approval.getSubmittedBy().equals(current.user().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有提交人可以撤回");
        }

        if (!approval.isPending()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该审批已处理，无法撤回");
        }

        AssetVersion version = versions.findById(approval.getAssetVersionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "版本不存在"));

        // 删除审批记录，版本状态改回 DRAFT
        approvals.delete(approval);
        version.setStatus(AssetVersion.Status.DRAFT);
    }

    public List<Approval> getPendingApprovals() {
        return approvals.findPending();
    }

    public List<Approval> getVersionApprovals(UUID versionId) {
        return approvals.findByAssetVersionIdOrderBySubmittedAtDesc(versionId);
    }

    private String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
