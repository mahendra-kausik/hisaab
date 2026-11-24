"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Building2,
  ArrowLeftRight,
  ChartColumn,
  ChevronDown,
  HandCoins,
  House,
  PiggyBank,
  Plus,
  Repeat,
  Tags,
  Users,
  Wallet,
  type LucideIcon,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { useDueAlerts } from "@/components/dues/dues-banner";
import { useQuickAdd } from "@/components/transactions/quick-add-provider";
import { cn } from "@/lib/utils";
import type { Me } from "@/lib/queries/me";
import { UserMenu } from "./user-menu";
import { WorkspaceSwitcher } from "./workspace-switcher";

type NavItem = { label: string; icon: LucideIcon; href: string };

// The everyday pages. Settings lives in the profile menu (user-menu.tsx).
const MAIN: NavItem[] = [
  { label: "Home", icon: House, href: "/" },
  { label: "Transactions", icon: ArrowLeftRight, href: "/transactions" },
  { label: "Accounts", icon: Wallet, href: "/accounts" },
  { label: "Dues", icon: HandCoins, href: "/dues" },
  { label: "Reports", icon: ChartColumn, href: "/reports" },
];

// Less-used pages, collapsed under "More".
const MORE: NavItem[] = [
  { label: "Budgets", icon: PiggyBank, href: "/budgets" },
  { label: "Recurring", icon: Repeat, href: "/recurring" },
  { label: "Categories", icon: Tags, href: "/categories" },
  { label: "Parties", icon: Users, href: "/parties" },
  { label: "Projects", icon: Building2, href: "/projects" },
];

const ITEM = "flex min-h-11 items-center gap-3 rounded-md px-3 text-sm font-medium";

/** A sub-page (a party's ledger under /parties) keeps its section highlighted. */
export function isCurrent(pathname: string, href: string): boolean {
  return pathname === href || (href !== "/" && pathname.startsWith(`${href}/`));
}

export function Sidebar({ me, onNavigate }: { me: Me; onNavigate?: () => void }) {
  const pathname = usePathname();
  const { openAdd } = useQuickAdd();
  const { counts } = useDueAlerts();
  const needAttention = counts.overdue + counts.today;
  const moreCurrent = MORE.some((i) => isCurrent(pathname, i.href));

  function link({ label, icon: Icon, href }: NavItem) {
    const current = isCurrent(pathname, href);
    return (
      <Link
        key={label}
        href={href}
        onClick={onNavigate}
        aria-current={current ? "page" : undefined}
        className={cn(
          ITEM,
          "outline-none transition-colors hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50",
          current && "bg-muted text-primary",
        )}
      >
        <Icon className="h-5 w-5" />
        {label}
        {href === "/dues" && needAttention > 0 && (
          <span className="ml-auto rounded-md bg-destructive px-1.5 py-0.5 text-xs font-medium text-white">
            {needAttention}
            <span className="sr-only"> need attention</span>
          </span>
        )}
      </Link>
    );
  }

  return (
    <div className="flex h-full flex-col gap-4 p-3">
      <WorkspaceSwitcher />

      <Button
        className="h-11 w-full justify-start gap-3 px-3"
        onClick={() => {
          onNavigate?.(); // closes the slide-over menu on narrow screens
          openAdd();
        }}
      >
        <Plus className="h-5 w-5" />
        Add
        <kbd className="ml-auto rounded-md bg-primary-foreground/20 px-1.5 py-0.5 font-sans text-xs font-normal">
          N
        </kbd>
      </Button>

      {/* Only the link list scrolls; the switcher, Add button and user menu stay pinned. */}
      <nav aria-label="Main" className="flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto">
        {MAIN.map(link)}

        {/* Native disclosure: no state to manage. Opens by itself when the page is inside it. */}
        <details open={moreCurrent || undefined} className="group">
          <summary
            className={cn(
              ITEM,
              "cursor-pointer list-none text-muted-foreground outline-none transition-colors hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50 [&::-webkit-details-marker]:hidden",
            )}
          >
            <ChevronDown className="h-5 w-5 transition-transform group-open:rotate-180" />
            More
          </summary>
          <div className="mt-1 flex flex-col gap-1">{MORE.map(link)}</div>
        </details>
      </nav>

      <UserMenu me={me} onNavigate={onNavigate} />
    </div>
  );
}
