-- Categories are expense-only (no income categories) and the defaults shrink. Existing workspaces
-- lose every income category and the named default expense categories; custom ones are kept.
--
-- Runs as hisaab_owner. These tables have FORCE ROW LEVEL SECURITY, which applies to the owner
-- too, and a migration has no current user, so every row would be filtered out and the
-- statements below would silently change nothing. FORCE is lifted for this migration only
-- (DDL is transactional, so a failure rolls it all back, FORCE included).
ALTER TABLE categories   NO FORCE ROW LEVEL SECURITY;
ALTER TABLE transactions NO FORCE ROW LEVEL SECURITY;
ALTER TABLE dues         NO FORCE ROW LEVEL SECURITY;

CREATE TEMP TABLE doomed ON COMMIT DROP AS
SELECT id FROM categories
WHERE kind = 'income'
   OR (is_system AND name IN (
         'Groceries', 'Eating Out', 'Food Delivery', 'Fuel', 'Cab/Auto', 'Public Transport',
         'Housing', 'Rent', 'Maintenance', 'Utilities', 'Mobile', 'Internet', 'Electricity',
         'Health', 'Travel', 'Personal Care', 'Gifts & Donations', 'Fees & Charges',
         'Money Lent', 'Loan Repayment'));

-- A custom subcategory of a deleted parent is kept: it moves to the top level.
-- ponytail: fails if a top-level category with the same name exists; fine for pre-deploy data.
UPDATE categories SET parent_id = NULL
WHERE parent_id IN (SELECT id FROM doomed) AND id NOT IN (SELECT id FROM doomed);

-- Transactions and dues keep their rows but lose the category (these foreign keys have no
-- ON DELETE action). Recurring rules are set to NULL and budgets cascade through their own keys.
UPDATE transactions SET category_id = NULL WHERE category_id IN (SELECT id FROM doomed);
UPDATE dues         SET category_id = NULL WHERE category_id IN (SELECT id FROM doomed);

-- One statement, so a deleted parent and its deleted children go together.
DELETE FROM categories WHERE id IN (SELECT id FROM doomed);

ALTER TABLE categories   FORCE ROW LEVEL SECURITY;
ALTER TABLE transactions FORCE ROW LEVEL SECURITY;
ALTER TABLE dues         FORCE ROW LEVEL SECURITY;
