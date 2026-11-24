"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ArrowLeftRight, HandCoins, House, Menu, Plus, type LucideIcon } from "lucide-react";
import { useDueAlerts } from "@/components/dues/dues-banner";
import { useQuickAdd } from "@/components/transactions/quick-add-provider";
import { cn } from "@/lib/utils";
import { isCurrent } from "./sidebar";

const TAB =
  "relative flex min-h-11 flex-col items-center justify-center gap-0.5 text-xs font-medium outline-none focus-visible:ring-3 focus-visible:ring-ring/50";

const LEFT: { label: string; icon: LucideIcon; href: string }[] = [
  { label: "Home", icon: House, href: "/" },
  { label: "Transactions", icon: ArrowLeftRight, href: "/transactions" },
];

/** Phone-only (< 768px) navigation: Home, Transactions, Add (+), Dues, More. */
export function BottomTabs({ onMore }: { onMore: () => void }) {
  const pathname = usePathname();
  const { openAdd } = useQuickAdd();
  const { counts } = useDueAlerts();
  const needAttention = counts.overdue + counts.today;

  const onDues = isCurrent(pathname, "/dues");
  // More stands in for every page that has no tab of its own (Accounts, Reports, Settings, ...).
  const onMoreTab = !onDues && !LEFT.some((t) => isCurrent(pathname, t.href));

  function link(label: string, Icon: LucideIcon, href: string, current: boolean) {
    return (
      <Link
        key={label}
        href={href}
        aria-current={current ? "page" : undefined}
        className={cn(TAB, current ? "text-primary" : "text-muted-foreground")}
      >
        <Icon className="h-6 w-6" />
        {label}
        {href === "/dues" && needAttention > 0 && (
          <span className="absolute top-1 left-1/2 ml-2 rounded-md bg-destructive px-1.5 text-xs font-medium text-white">
            {needAttention}
            <span className="sr-only"> need attention</span>
          </span>
        )}
      </Link>
    );
  }

  return (
    <nav
      aria-label="Primary"
      className="fixed inset-x-0 bottom-0 z-40 grid h-16 grid-cols-5 border-t bg-surface md:hidden"
    >
      {LEFT.map((t) => link(t.label, t.icon, t.href, isCurrent(pathname, t.href)))}

      <button type="button" onClick={openAdd} className={cn(TAB, "text-foreground")}>
        <span className="-mt-5 flex size-12 items-center justify-center rounded-full bg-primary text-primary-foreground shadow-md">
          <Plus className="h-6 w-6" />
        </span>
        <span className="sr-only">Add transaction</span>
      </button>

      {link("Dues", HandCoins, "/dues", onDues)}

      <button
        type="button"
        onClick={onMore}
        aria-current={onMoreTab ? "page" : undefined}
        className={cn(TAB, onMoreTab ? "text-primary" : "text-muted-foreground")}
      >
        <Menu className="h-6 w-6" />
        More
      </button>
    </nav>
  );
}
