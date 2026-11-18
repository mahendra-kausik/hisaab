"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  ArrowLeftRight,
  ChartColumn,
  HandCoins,
  House,
  PiggyBank,
  Settings,
  type LucideIcon,
} from "lucide-react";
import { cn } from "@/lib/utils";
import type { Me } from "@/lib/queries/me";
import { UserMenu } from "./user-menu";
import { WorkspaceSwitcher } from "./workspace-switcher";

// A page with no `href` yet is shown as "Soon". Each unit that builds a page adds its href.
const NAV: { label: string; icon: LucideIcon; href?: string }[] = [
  { label: "Home", icon: House, href: "/" },
  { label: "Transactions", icon: ArrowLeftRight },
  { label: "Dues", icon: HandCoins },
  { label: "Reports", icon: ChartColumn },
  { label: "Budgets", icon: PiggyBank },
  { label: "Settings", icon: Settings },
];

const ITEM = "flex min-h-11 items-center gap-3 rounded-md px-3 text-sm font-medium";

export function Sidebar({ me, onNavigate }: { me: Me; onNavigate?: () => void }) {
  const pathname = usePathname();

  return (
    <div className="flex h-full flex-col gap-4 p-3">
      <WorkspaceSwitcher />

      <nav aria-label="Main" className="flex flex-1 flex-col gap-1">
        {NAV.map(({ label, icon: Icon, href }) =>
          href ? (
            <Link
              key={label}
              href={href}
              onClick={onNavigate}
              aria-current={pathname === href ? "page" : undefined}
              className={cn(
                ITEM,
                "outline-none transition-colors hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50",
                pathname === href && "bg-muted text-primary",
              )}
            >
              <Icon className="h-5 w-5" />
              {label}
            </Link>
          ) : (
            <span key={label} aria-disabled="true" className={cn(ITEM, "text-muted-foreground")}>
              <Icon className="h-5 w-5" />
              {label}
              <span className="ml-auto rounded-md bg-subtle px-1.5 py-0.5 text-xs font-normal">Soon</span>
            </span>
          ),
        )}
      </nav>

      <UserMenu me={me} />
    </div>
  );
}
