# UI Context

## Theme

Calm, clear, and trustworthy — closer to a well-made
banking app than a flashy dashboard. Light and dark
modes, following the system preference with a manual
toggle. Desktop-first: the first business user works on
a computer, so Phase 1 and 1.5 are designed for laptop
and desktop screens. Phone-optimized layouts come
afterwards, but every page must stay usable on a phone.
Large readable numbers, generous spacing, minimal
decoration, and a single accent color. Clarity beats
density. Every primary flow works from the keyboard.

## Colors

All components use these tokens via CSS custom
properties defined in `app/globals.css`. No hardcoded
hex values in components. shadcn/ui variables
(`--background`, `--foreground`, `--card`, `--primary`,
`--muted`, `--border`, `--destructive`, `--ring`, etc.)
are defined in terms of these tokens.

| Role                           | CSS Variable          | Light     | Dark      |
| ------------------------------ | --------------------- | --------- | --------- |
| Page background                | `--bg-base`           | `#F7F8FA` | `#0B0F14` |
| Surface (cards, sheets)        | `--bg-surface`        | `#FFFFFF` | `#121821` |
| Subtle surface (hover, inputs) | `--bg-subtle`         | `#F1F3F6` | `#1A222D` |
| Primary text                   | `--text-primary`      | `#111827` | `#E6EAF0` |
| Muted text                     | `--text-muted`        | `#5B6472` | `#94A3B8` |
| Primary accent                 | `--accent-primary`    | `#0F766E` | `#2DD4BF` |
| Text on accent                 | `--accent-foreground` | `#FFFFFF` | `#042F2E` |
| Border                         | `--border-default`    | `#E5E7EB` | `#243041` |
| Focus ring                     | `--ring`              | `#14B8A6` | `#5EEAD4` |
| Error                          | `--state-error`       | `#DC2626` | `#F87171` |
| Warning                        | `--state-warning`     | `#B45309` | `#FBBF24` |
| Success                        | `--state-success`     | `#15803D` | `#4ADE80` |
| Money in (income, received)    | `--money-in`          | `#15803D` | `#4ADE80` |
| Money out (expense, paid)      | `--money-out`         | `#B42318` | `#F87171` |

Rules:

- Color is never the only signal. Amounts also carry a
  sign or label ("+₹2,000", "Paid", "Received"), and due
  states always show a text badge.
- Text contrast meets WCAG AA in both modes.
- Never name a Tailwind theme color `base` (or any name that
  is also a font size or spacing key such as `sm`, `lg`,
  `xs`): `--color-base` made `text-base` paint text in the
  page-background color. The page background is
  `bg-background`.

## Typography

| Role      | Font       | Variable      |
| --------- | ---------- | ------------- |
| UI text   | Geist Sans | `--font-sans` |
| Code/mono | Geist Mono | `--font-mono` |

- Base size 16px; content text never below 14px.
- All amounts use `tabular-nums` so columns line up.
  Amounts use the sans font, not mono.
- Headline amounts (balances, report totals): 28–36px,
  semibold.

## Formatting

- Money: Indian digit grouping with the rupee symbol,
  e.g. ₹1,23,456. Show paise only when non-zero. Always
  use the shared `formatINR(paise)` helper (built on
  `Intl.NumberFormat('en-IN', ...)`); never format
  inline.
- Compact amounts for summaries: ₹1.2L, ₹3.4Cr, via
  `formatINRCompact(paise)`. Below ₹1 lakh it returns the
  full `formatINR` output. From ₹1 lakh up it shows one
  decimal with a trailing `.0` dropped (₹1.2L, ₹10L,
  ₹3.4Cr), truncated rather than rounded so a summary
  never overstates a total.
- Dates: `4 Nov 2026`. Recent dates show "Today" or
  "Yesterday". Due dates show "in 3 days" or "5 days
  overdue".
- Amount input: the user types rupees (decimals
  allowed). Convert to paise with the shared
  `rupeesToPaise` helper using string parsing, never
  float multiplication. It accepts digits with optional
  comma grouping and at most two decimals ("1,000.5" →
  100050). It rejects empty input, negatives, three or
  more decimals, and non-numeric text. "0" returns 0;
  the "amount must be greater than zero" rule belongs to
  form validation.

## Border Radius

| Context                                     | Class                                         |
| ------------------------------------------- | --------------------------------------------- |
| Inline / small UI (badges, inputs, buttons) | `rounded-md`                                  |
| Cards / panels                              | `rounded-xl`                                  |
| Modals / overlays / sheets                  | `rounded-2xl` (bottom sheets: top corners only) |

## Component Library

shadcn/ui on top of Tailwind. Components live in
`components/ui/` and are added with the shadcn CLI, not
written from scratch. Feature components live in
`components/<feature>/` (e.g. `components/transactions/`,
`components/dues/`). Charts use Recharts, styled with
the color tokens.

## Layout Patterns

- **Desktop (≥ 768px, primary):** fixed 240px left
  sidebar with the workspace switcher at the top, a
  prominent Add button, then Home, Transactions, Accounts,
  Dues, Reports and a collapsible "More" group (Budgets,
  Recurring, Categories, Parties, Projects; it opens by itself
  when the current page is inside it). Settings is not in the
  list: it is "Profile & settings" in the user menu at the
  bottom. Only the link list scrolls (the switcher, Add button
  and user menu stay pinned). Content max width 1100px.
  Design and verify at 1280px first.
- **Phone (< 768px):** a 56px top bar (app name, workspace
  switcher) and a fixed 64px bottom tab bar: Home,
  Transactions, a raised round Add (+), Dues (with the
  needs-attention count badge) and More. The current tab is
  `text-primary` with `aria-current="page"`; More is current
  on any page that has no tab of its own. More opens the full
  sidebar (every page, user menu, theme) as a sheet from the
  right. Single-column content with 16px side padding, 96px
  bottom padding so the bar never covers the last row, and no
  horizontal overflow at 360px.
- **App icon and install (PWA):** `app/icon.svg` is the one
  icon source (white "H" on `--accent-primary` light, `#0F766E`,
  art inside the central 80% so it also works maskable).
  `app/manifest.ts` makes the app installable (standalone
  window, theme `#0F766E`, splash `#F7F8FA`); the browser
  chrome follows the page background per colour scheme. No
  service worker: online-only. The PNG sizes are rendered from
  the SVG with the `sharp` command noted in `manifest.ts`.
- **Toasts on phones** sit above the tab bar (`--tab-bar-h`
  is 4rem below 768px, 0 above).
- **Theme choice:** System, Light or Dark from the user
  menu, kept in `localStorage` (`hisaab.theme`; System is
  "no value"). A small script in `<head>` applies it before
  first paint so there is no flash.
- **Quick add:** a dialog, opened by the Add button or
  the `N` key from any page; Enter saves, Esc closes.
  Below 768px it is a bottom sheet (full width, top corners
  rounded, slides up, at most 90% of the screen height) whose
  Cancel / Save footer stays pinned while the form scrolls.
  The amount field comes first, is autofocused, and uses
  `inputmode="decimal"`. Then a type toggle (Expense /
  Income / Transfer), a searchable category picker
  (recent first; expense only, since income has no
  categories), account and date prefilled, an
  optional note, and Save. Tab order follows the visual
  order.
- **Due reminders:** a dismissible banner at the top of
  Home ("3 dues overdue · 1 due today") linking to Dues,
  and a count badge on the Dues nav item. Dismissal is per
  workspace and lasts until the next day; the badge never hides.
- **Lists:** grouped by date with a daily total. Each
  row shows the category icon, note or party name,
  account, and the amount right-aligned. The daily total is
  net: income minus expense, with a sign (`+₹2,000`,
  `−₹1,250`); transfers are left out. A transfer is one row
  ("Cash → HDFC Bank", neutral colour) even though it is two
  records. A row opens its edit dialog on click or Enter, and
  has an actions menu (Edit, Delete).
- **Exports:** top right of the Transactions page, two outline
  `<a download>` links (32px): "Export all (Excel)" (whole workspace,
  ignores the filters, `.../export.xlsx`) and "Export CSV" (current
  filters). The row wraps on narrow screens.
- **Filters:** the Transactions page keeps its filters
  (search, type, account, category, party, project, tag, date range) in the
  URL, so a reload or shared link shows the same list. They
  stay in a two-column grid on phones.
- **Accounts page (`/accounts`):** a list of open accounts, then an
  "Archived" section. Each row shows a type icon, the name, the type
  and the balance, and links to `/transactions?account_id=…`; the row
  menu has Edit and Archive / Unarchive (no confirm: both reverse).
  A negative balance (money owed) shows `−₹12,000` in the money-out
  colour. The opening balance field accepts a leading minus.
- **Parties page (`/parties`):** the same layout as Accounts: open
  parties, then "Archived". Each row shows the name, role and phone
  and links to the party's ledger (`/parties/{id}`). The transaction and
  recurring forms show an optional
  "Party" select only once the workspace has a party; an archived
  party is listed there only while it is the saved choice.
- **Party ledger (`/parties/{id}`):** back link, name with role and
  phone, an outline "Download statement" link (a plain `<a download>` to
  `.../statement.pdf`, like Export CSV) beside an "Add due" button (party
  preset; both 44px, the row wraps on narrow screens), three tiles (Total paid,
  Total received, Net balance as "They owe you ₹X" / "You owe them ₹X" /
  "Settled up"; net comes from the party's non-cancelled dues), the
  party's dues (cancelled last), then their transactions (the normal
  list filtered by `party_id`). Total paid and received are summed in
  the browser over every page of that party's transactions.
- **Dues page (`/dues`):** sections Overdue, Due today, Upcoming (next
  7 days), Later (no date or further out), Settled, Cancelled; empty
  sections are hidden. A row shows the party (link to the ledger), a
  state text badge, the reason, "Due today / in 3 days / 5 days
  overdue", and the outstanding amount labelled "They owe you" (money
  in colour) or "You owe" (money out colour), with "of ₹total" once part
  paid. Row menu: Record payment (not for settled or cancelled), Edit,
  Cancel due / Reopen (no confirm: both reverse). Add / edit dialog:
  direction toggle (They owe me / I owe them), party, amount, reason,
  optional due date, project and category; party and direction are
  fixed when editing. When adding, the checkbox "Record the money I
  handed over now" (receivable) / "received" (payable) reveals account,
  date, category and note and sends them as `origin`. The payment
  dialog prefills the outstanding amount; an overpayment shows the
  API's message.
- **Projects page (`/projects`):** the same layout as Parties, minus
  role and phone: the row shows the name and notes and links to
  `/transactions?project_id=…`. The transaction and recurring forms
  show an optional "Project" select under the party one, only once
  the workspace has a project. Transaction rows list the project
  name among the detail chips.
- **Budgets page (`/budgets`):** each budgeted category in the
  current period has outline Edit and Remove buttons (Remove sets the
  amount to 0, which the API treats as removal); past periods are
  read-only. A new budget shows what is already spent without a reload.
- **Categories page (`/categories`):** one plain list (no Expense or
  Income headings, since categories are expense-only) with a single
  "Add category" button; parents with their subcategories indented. Row menu: Rename, Add
  subcategory (parents only), Hide / Show, Delete (custom categories
  only, through a confirm dialog). A hidden category carries a
  "Hidden" text badge; hiding a parent also hides its subcategories
  in the pickers.
- **Attachments (U19):** an "Attachments" section at the bottom of
  the transaction edit dialog (not for transfers) and the due edit
  dialog, above the error line and footer. A heading with an
  "Attach file" outline button (paperclip icon), then one row per
  file: the name (truncated) and size in a link that opens the file
  in a new tab, and a trash button. The file input is hidden (`sr-only`)
  and opened from the button; accepted types are JPEG, PNG, WebP,
  HEIC and PDF, up to 10 MB, checked in the browser before the
  upload and again by the API. Files upload and delete at once, not
  on Save, and the empty state says so. When adding (nothing saved
  yet) the section is the single line "Save first, then edit to
  attach photos or PDFs." Every button in it is `type="button"`
  because it sits inside the dialog's form.
- **Empty states:** one sentence and one primary action
  ("No transactions yet — Add your first expense").
- **Destructive actions:** a confirm dialog for deletes that
  cannot be undone. A soft delete (a transaction or transfer)
  takes effect at once and shows an Undo toast for 8 seconds,
  with no confirm dialog. Removing an attachment cannot be
  undone, and a second dialog on top of the edit dialog is
  heavy, so its row swaps the trash button for inline
  Keep / Remove buttons.
- **Click/tap targets:** at least 44×44px, so phone use
  works later without redesign.
- **Keyboard:** visible focus rings on every interactive
  element; dialogs trap focus and return it on close.
- **Loading:** skeletons for lists and cards; no
  full-page spinners.

## Icons

Lucide React, stroke icons only. Sizes: `h-4 w-4`
inline, `h-5 w-5` in buttons and the sidebar, `h-6 w-6`
in the bottom tab bar. Default categories map to Lucide
icons in a frontend lookup; custom categories use a
generic default icon.
