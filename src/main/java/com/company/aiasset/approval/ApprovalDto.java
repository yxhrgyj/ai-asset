package com.company.aiasset.approval;

import java.time.Instant;
import java.util.UUID;

public class ApprovalDto {

    public record Item(
            UUID id,
            UUID assetVersionId,
            UUID assetId,
            String assetName,
            int versionNo,
            UUID submittedBy,
            String submittedByName,
            Instant submittedAt,
            UUID decidedBy,
            String decidedByName,
            Instant decidedAt,
            String decision,
            String comment
    ) {}
}
