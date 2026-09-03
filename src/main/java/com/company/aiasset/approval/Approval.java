package com.company.aiasset.approval;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * 审批记录。
 *
 * 核心约束（由数据库保证）：
 * - 审批人不能是提交人（approvals_approver_not_submitter）
 * - 决定三元组必须同时有或同时无（approvals_decision_triple）
 */
@Entity
@Table(name = "approvals")
public class Approval {

    public enum Decision { APPROVED, REJECTED }

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "asset_version_id", nullable = false, columnDefinition = "uuid")
    private UUID assetVersionId;

    @Column(name = "submitted_by", nullable = false, columnDefinition = "uuid")
    private UUID submittedBy;

    @Column(name = "submitted_at", nullable = false, insertable = false, updatable = false)
    private Instant submittedAt;

    /** 提交时冻结的内容哈希，用于检测审批期间内容是否被修改 */
    @Column(name = "frozen_hash", nullable = false)
    private String frozenHash;

    @Column(name = "decided_by", columnDefinition = "uuid")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision")
    private Decision decision;

    @Column(columnDefinition = "text")
    private String comment;

    public boolean isPending() {
        return decidedAt == null;
    }

    public UUID getId() { return id; }

    public UUID getAssetVersionId() { return assetVersionId; }
    public void setAssetVersionId(UUID assetVersionId) { this.assetVersionId = assetVersionId; }

    public UUID getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(UUID submittedBy) { this.submittedBy = submittedBy; }

    public Instant getSubmittedAt() { return submittedAt; }

    public String getFrozenHash() { return frozenHash; }
    public void setFrozenHash(String frozenHash) { this.frozenHash = frozenHash; }

    public UUID getDecidedBy() { return decidedBy; }
    public void setDecidedBy(UUID decidedBy) { this.decidedBy = decidedBy; }

    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }

    public Decision getDecision() { return decision; }
    public void setDecision(Decision decision) { this.decision = decision; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
