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
  Categories, Dues, Reports, Budgets, Settings. Content max width 1100px.
  Design and verify at 1280px first.
- **Narrow screens (< 768px, for now):** the sidebar
  collapses into a slide-over menu opened from a top app
  bar. Single-column content with 16px side padding and
  no horizontal overflow at 360px.
- **Theme choice:** System, Light or Dark from the user
  menu, kept in `localStorage` (`hisaab.theme`; System is
  "no value"). A small script in `<head>` applies it before
  first paint so there is no flash.
- **Unbuilt pages:** sidebar items whose page does not exist
  yet are shown muted with a "Soon" badge and are not links;
  the unit that builds the page gives the item its `href`.
- **Phone layout (later, U23):** bottom tab bar with
  Home, Transactions, Add (+), Dues, More; quick add as
  a bottom sheet.
- **Quick add:** a dialog, opened by the Add button or
  the `N` key from any page; Enter saves, Esc closes.
  The amount field comes first, is autofocused, and uses
  `inputmode="decimal"`. Then a type toggle (Expense /
  Income / Transfer), a searchable category picker
  (recent first), account and date prefilled, an
  optional note, and Save. Tab order follows the visual
  order.
- **Due reminders:** a dismissible banner at the top of
  Home ("3 dues overdue · 1 due today") linking to Dues,
  and a count badge on the Dues nav item.
- **Lists:** grouped by date with a daily total. Each
  row shows the category icon, note or party name,
  account, and the amount right-aligned. The daily total is
  net: income minus expense, with a sign (`+₹2,000`,
  `−₹1,250`); transfers are left out. A transfer is one row
  ("Cash → HDFC Bank", neutral colour) even though it is two
  records. A row opens its edit dialog on click or Enter, and
  has an actions menu (Edit, Delete).
- **Filters:** the Transactions page keeps its filters
  (search, type, account, category, party, tag, date range) in the
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
  and links to `/transactions?party_id=…` (the full party ledger comes
  with dues). The transaction and recurring forms show an optional
  "Party" select only once the workspace has a party; an archived
  party is listed there only while it is the saved choice.
- **Categories page (`/categories`):** Expense and Income sections,
  parents with their subcategories indented. Row menu: Rename, Add
  subcategory (parents only), Hide / Show, Delete (custom categories
  only, through a confirm dialog). A hidden category carries a
  "Hidden" text badge; hiding a parent also hides its subcategories
  in the pickers.
- **Empty states:** one sentence and one primary action
  ("No transactions yet — Add your first expense").
- **Destructive actions:** a confirm dialog for deletes that
  cannot be undone. A soft delete (a transaction or transfer)
  takes effect at once and shows an Undo toast for 8 seconds,
  with no confirm dialog.
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
