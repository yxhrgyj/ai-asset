-- Retire the approval workflow without deleting its historical records.
-- Pending versions return to their authors; no version is automatically published.
UPDATE asset_versions SET status = 'DRAFT' WHERE status = 'PENDING';

-- Recover a latest rejected version only when it cannot conflict with another open draft.
UPDATE asset_versions v SET status = 'DRAFT'
WHERE v.status = 'REJECTED'
  AND NOT EXISTS (
      SELECT 1 FROM asset_versions newer
      WHERE newer.asset_id = v.asset_id AND newer.version_no > v.version_no
  )
  AND NOT EXISTS (
      SELECT 1 FROM asset_versions draft
      WHERE draft.asset_id = v.asset_id AND draft.status = 'DRAFT'
  );

UPDATE users SET role = 'AUTHOR' WHERE role = 'APPROVER';
