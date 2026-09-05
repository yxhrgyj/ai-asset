package com.company.aiasset.approval;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalRepository extends JpaRepository<Approval, UUID> {

    /** 查询待审批列表（未决定的） */
    @Query("SELECT a FROM Approval a WHERE a.decidedAt IS NULL ORDER BY a.submittedAt")
    List<Approval> findPending();

    /** 统计待审批数量 */
    @Query("SELECT COUNT(a) FROM Approval a WHERE a.decidedAt IS NULL")
    long countPending();

    /** 查询某个版本的所有审批记录（包括历史） */
    List<Approval> findByAssetVersionIdOrderBySubmittedAtDesc(UUID assetVersionId);

    /** 查询某个版本当前待审批的记录 */
    @Query("SELECT a FROM Approval a WHERE a.assetVersionId = ?1 AND a.decidedAt IS NULL")
    Optional<Approval> findPendingByVersion(UUID assetVersionId);

    /** 查询某个版本是否有已批准的记录 */
    @Query("SELECT COUNT(a) > 0 FROM Approval a WHERE a.assetVersionId = ?1 AND a.decision = 'APPROVED'")
    boolean hasApproved(UUID assetVersionId);
}
