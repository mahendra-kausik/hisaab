# Hisaab (working name)

## Overview

Hisaab is a multi-user money tracker for individuals and
small, informal businesses in India. People record
expenses and income by hand, organise them into
categories and accounts, set budgets, and get a clear
month-end report. Alongside personal tracking, it keeps
a simple people-and-dues ledger: who was paid and for
what, who owes money, how much is still pending, and by
when. This serves users like a small real estate
business owner who wants clarity without proper
accounting software. It is built in phases: manual
tracking first, then automatic transaction import, AI
insights, and later tax and business features.

## Goals

1. A new user can sign up and record their first expense
   in under 60 seconds on a computer, using only the
   keyboard if they prefer.
2. Phase 1 is in regular use by at least 3 real users,
   including one small business user, for 4 consecutive
   weeks.
3. Zero cross-user data leaks: every workspace-scoped
   endpoint has automated tests proving a non-member
   cannot read or modify its data.
4. Month-end report numbers always reconcile exactly
   with the underlying transactions.
5. The dues ledger replaces the business user's
   notebook and WhatsApp tracking of who owes what.

## Core User Flow

### Personal tracking (Phase 1)

1. User signs up with email and password.
2. A personal workspace is created with default
   categories and a Cash account.
3. User adds an expense or income from the quick-add
   sheet (amount, type, category; account and date
   prefilled; optional note and tags).
4. User browses, filters, searches, edits, and deletes
   transactions.
5. User sets monthly budgets per category and sees
   progress.
6. At period end, user opens the monthly report: totals,
   category breakdown, daily spend, comparison with the
   previous period.

### Business dues (Phase 1.5)

1. User creates a business workspace (e.g. "Dad — Real
   Estate").
2. User adds parties (buyers, contractors, brokers,
   suppliers) and projects (properties or sites).
3. User records payments made to or received from a
   party, with a purpose and optionally a project.
4. User records a due: who owes whom, how much, for
   what, and by when. Lending money is recorded in one
   step as an expense plus a receivable due.
5. User records part payments against the due; the
   outstanding amount updates automatically.
6. User is reminded about overdue, due-today, and
   upcoming dues (in the app and by a daily email
   digest) and reviews them on the Dues screen.
   Reminders go only to the user, never to the party.
7. User opens a party's page to see their full history
   and current balance.

## Features

### Users & Workspaces

- Sign up, log in, log out, session refresh.
- Each user gets a personal workspace and can create
  more workspaces of kind `personal` or `business`, and
  switch between them.
- Workspace kind only changes default seed data; every
  feature is available in every workspace.
- Per-user settings: name, timezone (default
  `Asia/Kolkata`), period start day (1–28, default 1;
  e.g. 25 for someone paid on the 25th), daily dues
  digest email on/off (default on).
- Currency is INR only.

### Money Accounts

- Account types: cash, bank, credit card, wallet. Name,
  type, opening balance, archive. Names are unique within
  a workspace, ignoring case.
- Current balance is computed from the opening balance
  and the account's transactions.

### Categories & Tags

- Two-level categories (parent → child), separate sets
  for expense and income.
- Default categories seeded per workspace by kind (see
  Default Data). Users can rename, add, and hide
  categories. Hiding a parent category also hides its
  subcategories in pickers. Categories in use cannot be
  hard-deleted.
  Default (seeded) categories can be renamed or hidden but
  never deleted; categories the user adds can be deleted
  while unused. Sibling names are unique, ignoring case.
- Free-form tags on transactions.

### Transactions

- Types: expense, income, and transfer between two of
  the workspace's own accounts.
- Fields: amount, date, account, category (optional;
  shown as "Uncategorized" when missing), note, tags,
  payment method (cash, UPI, card, bank transfer,
  cheque, other), optional party, optional project.
- Edit, soft delete, and restore (Undo).
- List with filters (date range, type, account,
  category, tag, party, project) and text search on
  notes; newest first with cursor pagination.
- Transfers never count as income or expense.

### Budgets

- Monthly budget per parent expense category.
- Progress shown with a warning at 80% and over-budget
  state at 100% (in-app only, no notifications).

### Recurring Entries

- Recurring rules for expenses and income (weekly,
  monthly, yearly), e.g. rent, subscriptions, mess fees.
- A background job creates each occurrence exactly once.
- Upcoming occurrences are visible; rules can be paused
  or ended.

### Reports

- Monthly report for a period, respecting the user's
  period start day: total income, total expense, net;
  spend by category (donut chart + table); daily spend
  (bar chart); comparison with the previous period; top
  5 expenses.
- CSV export of transactions for a date range.

### Parties, Projects & Dues (Phase 1.5)

- Parties: name, phone (optional), role (buyer, seller,
  contractor, broker, supplier, tenant, friend/family,
  other), notes.
- Projects: name (e.g. "Plot 14, Whitefield"), notes,
  archive. Any transaction or due can be tagged with a
  project.
- Dues: direction (`receivable` = they owe the user;
  `payable` = the user owes them), party, amount,
  reason, due date, optional project and category.
- Payments against a due are ordinary transactions
  linked to it. Outstanding = due amount − linked
  payments. Overpayment is rejected.
- Lending and borrowing: lending money counts as an
  expense, and borrowing counts as income. When creating
  a due, the user can also record the money handed over
  in the same step (an expense for a receivable, income
  for a payable). This origin transaction is not a
  payment and does not reduce the outstanding amount.
  Repayments are linked payments, so getting lent money
  back counts as income.
- Due states are derived: open, partially paid, settled,
  overdue. Cancelling a due is an explicit user action.
- Dues screen: overdue, due today, upcoming (next 7
  days), settled.
- Party page: all transactions and dues with that party,
  total paid, total received, and current net balance.
- Reminders go only to the user, never to the party:
  a banner on Home and a count badge on Dues for overdue
  and due-today items, plus a daily email digest listing
  overdue, due-today, and next-3-days dues across the
  user's workspaces (can be turned off in settings).
- Attachments: photos or PDFs of receipts, cheques, and
  agreements on a transaction or due. JPEG, PNG, WebP,
  HEIC or PDF, up to 10 MB each, no limit on how many.
  Added, opened and removed from the item's edit dialog
  (an item must be saved first); files are saved the moment
  they are added. Transfers cannot have attachments.
  Deleting a transaction hides its files, and restoring it
  brings them back.
- Per-party statement as a shareable PDF, downloaded from the
  party page. All time (no date range). It shows the totals
  (paid, received, net balance), the party's dues, and every
  transaction with date, note (or category), paid and received.
  Accounts, payment methods and tags are left out because the
  party sees the file. Text in scripts the PDF font cannot draw
  (for example Devanagari) prints as "?".
- Excel export of a workspace's transactions and dues.

### Phone Support (after Phase 1.5)

- Phase 1 and 1.5 are designed for laptop and desktop
  screens. Pages must stay usable on a phone, but
  phone-optimized layouts come later.
- Later: phone layout (bottom tab bar, bottom-sheet
  quick add) and PWA installability (manifest, icons).
  Online-only.

## Default Data

Every new workspace gets one Cash account, plus these
loan categories in addition to the lists below:
expense Money Lent and Loan Repayment; income Money
Borrowed and Repayment Received.

Personal workspace:

- Expense: Food (Groceries, Eating Out, Food Delivery),
  Transport (Fuel, Cab/Auto, Public Transport), Housing
  (Rent, Maintenance, Utilities), Bills & Recharges
  (Mobile, Internet, Electricity), Shopping, Health,
  Education, Entertainment, Subscriptions, Travel,
  Personal Care, Gifts & Donations, Fees & Charges,
  Other.
- Income: Salary, Stipend, Business Income, Refunds,
  Interest, Gifts Received, Other Income.

Business workspace:

- Expense: Labour, Materials, Contractor Payments,
  Broker Commission, Registration & Legal, Transport,
  Office & Admin, Other.
- Income: Sale Proceeds, Advance Received, Rent
  Received, Commission, Other Income.

## Roadmap (beyond current scope)

- **Phase 2 — Automatic import:** bank statement PDF/CSV
  upload with per-bank parsers and balance validation;
  email-forwarding inbox for bank alerts; one import
  pipeline (parse → normalize merchant → dedupe →
  categorize → review inbox); richer charts (trends,
  heatmap, Sankey, subscription detection).
- **Phase 2.5 — Mobile:** React Native (Expo) app,
  Android SMS feeder, iOS Shortcuts feeder.
- **Phase 3 — AI insights:** ML categorizer trained on
  user corrections, anomaly alerts, month-end forecasts,
  natural-language questions (validated read-only
  queries), monthly narrative summaries. Numbers are
  always computed by code, never by an LLM. Because the
  API is Java, model training may live in a small
  separate Python service that the API calls; decide at
  the start of Phase 3.
- **Phase 4 — Tax and business (on hold):** tax-relevant
  tagging, regime comparison, document vault; invoices,
  P&L, CA export.

## Scope

### In Scope (Phase 1 and 1.5)

- Everything under Features above.
- A desktop-first web app: designed for laptop and
  desktop screens, still usable on a phone.
- Email + password authentication.

### Out of Scope (for now)

- Automatic transaction import of any kind (statements,
  SMS, email, Account Aggregator).
- AI/ML features.
- Native mobile apps.
- Tax features, invoices, GST, P&L.
- Multi-currency.
- Sharing a workspace with other users (the schema
  supports membership; the UI does not).
- Google sign-in, email verification, and password reset
  (later units; they need an email provider — see Open
  Questions).
- Sending reminders or messages to parties (WhatsApp,
  SMS, or email).
- Phone-optimized layouts and PWA install (planned right
  after Phase 1.5).
- Push notifications and offline mode.
- Interest calculation on dues.
- Languages other than English.

## Success Criteria

1. A new user can sign up and immediately sees a
   personal workspace with default categories and a
   Cash account.
2. On desktop, an expense can be added from any page by
   pressing `N`, typing an amount, picking a category,
   and pressing Enter (account and date default
   sensibly). Every page also renders without horizontal
   overflow at 360px wide.
3. For every workspace-scoped endpoint, an automated test
   proves a non-member gets 404 and cannot mutate data,
   and a direct SQL test proves RLS blocks
   cross-workspace reads even without the app-level
   check.
4. Monthly report totals equal the sum of non-deleted,
   non-transfer transactions in the period for any
   period start day, with tests covering month
   boundaries, 28–31-day months, and leap years.
5. Running the recurring job twice for the same date
   creates each occurrence only once.
6. A receivable due of ₹1,00,000 with payments of
   ₹40,000 and ₹25,000 shows ₹35,000 outstanding and
   "partially paid", becomes "overdue" after its due
   date, and a further payment of ₹40,000 is rejected.
7. Account balances always equal the opening balance
   plus the account's inflows minus its outflows.
8. Lending ₹10,000 with "record money handed over"
   creates one ₹10,000 expense (Money Lent) and one
   ₹10,000 receivable due; a ₹4,000 repayment counts as
   income and leaves ₹6,000 outstanding.
9. The daily digest lists exactly the user's overdue,
   due-today, and next-3-days dues, and is sent at most
   once per user per day even if the job runs twice.
10. `make check` passes on every commit to `main`.
