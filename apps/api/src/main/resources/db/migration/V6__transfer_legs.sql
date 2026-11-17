-- U5: transfers. No new table: a transfer is two rows of `transactions` (transfer_out and
-- transfer_in) sharing a transfer_group_id, so RLS and the CHECK constraints from V5 already apply.
--
-- At most one leg of each type per group: a group can never grow a third row, and the API can
-- rely on "exactly one out and one in". The index also serves lookups by transfer_group_id.
CREATE UNIQUE INDEX transactions_transfer_leg_uq
  ON transactions (transfer_group_id, type)
  WHERE transfer_group_id IS NOT NULL;
