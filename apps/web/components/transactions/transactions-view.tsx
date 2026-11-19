"use client";

import { Download } from "lucide-react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { buttonVariants } from "@/components/ui/button";
import type { TransactionFilters } from "@/lib/queries/transactions";
import { useWorkspace } from "@/lib/workspace";
import { TransactionFilterBar } from "./transaction-filters";
import { TransactionList } from "./transaction-list";

const FILTER_KEYS = ["q", "type", "account_id", "category_id", "tag", "from", "to"] as const;

/**
 * The Transactions page body. The filters live in the URL (`?type=expense&q=petrol`), so a
 * reload or a shared link shows the same list and the Back button undoes a filter change.
 */
export function TransactionsView() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const { active } = useWorkspace();

  const filters: TransactionFilters = {};
  for (const key of FILTER_KEYS) {
    const value = params.get(key);
    if (value) filters[key] = value;
  }

  // Patches start from the live URL, not from `filters`, so two quick changes (a typed search
  // and a dropdown) cannot overwrite each other. An empty value removes that filter.
  function update(patch: Partial<Record<keyof TransactionFilters, string>>) {
    const next = new URLSearchParams(window.location.search);
    for (const [key, value] of Object.entries(patch)) {
      if (value) next.set(key, value);
      else next.delete(key);
    }
    const query = next.toString();
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });
  }

  // A plain link: the browser downloads the file with the session cookie, and the API applies the
  // same filters as the list. ponytail: no 401 refresh here; the page just loaded, so the session is
  // fresh. Add apiFetch blob support if long idle tabs hit it.
  const exportParams = new URLSearchParams(filters as Record<string, string>);
  const exportHref = `/api/v1/workspaces/${active.id}/transactions/export.csv?${exportParams}`;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex justify-end">
        <a href={exportHref} download className={buttonVariants({ variant: "outline" })}>
          <Download className="size-4" /> Export CSV
        </a>
      </div>
      <TransactionFilterBar filters={filters} onChange={update} />
      <TransactionList
        filters={filters}
        onClear={() =>
          update({ q: "", type: "", account_id: "", category_id: "", tag: "", from: "", to: "" })
        }
      />
    </div>
  );
}
