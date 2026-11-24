"use client";

import { useRef, useState } from "react";
import { Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAccounts } from "@/lib/queries/accounts";
import { useCategories } from "@/lib/queries/categories";
import { useParties } from "@/lib/queries/parties";
import { useProjects } from "@/lib/queries/projects";
import { useTags } from "@/lib/queries/tags";
import type { TransactionFilters } from "@/lib/queries/transactions";
import { categoryItems } from "@/lib/transactions";
import { useWorkspace } from "@/lib/workspace";
import { NativeSelect } from "./native-select";

// The API's type filter takes one leg of a transfer at a time, so a transfer is "out" or "in".
const TYPES = [
  { value: "expense", label: "Expense" },
  { value: "income", label: "Income" },
  { value: "transfer_out", label: "Transfer out" },
  { value: "transfer_in", label: "Transfer in" },
];

const SEARCH_DELAY_MS = 300;

function Field({ label, htmlFor, children }: { label: string; htmlFor: string; children: React.ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col gap-1">
      <Label htmlFor={htmlFor} className="text-xs text-muted-foreground">
        {label}
      </Label>
      {children}
    </div>
  );
}

/**
 * The filter bar. It holds no filter state of its own: `filters` come from the URL and every
 * change goes back out through `onChange` as a patch (an empty value removes that filter).
 */
export function TransactionFilterBar({
  filters,
  onChange,
}: {
  filters: TransactionFilters;
  onChange: (patch: Partial<Record<keyof TransactionFilters, string>>) => void;
}) {
  const { active } = useWorkspace();
  const accounts = useAccounts(active.id);
  const categories = useCategories(active.id);
  const tags = useTags(active.id);
  const parties = useParties(active.id);
  const projects = useProjects(active.id);

  // The box keeps what is being typed; the URL only gets it after a short pause.
  const [search, setSearch] = useState(filters.q ?? "");
  const [seenQ, setSeenQ] = useState(filters.q ?? "");
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);
  // If the URL's search changes from outside (browser Back, Clear), show it in the box.
  if ((filters.q ?? "") !== seenQ) {
    setSeenQ(filters.q ?? "");
    setSearch(filters.q ?? "");
  }

  function onSearchChange(value: string) {
    setSearch(value);
    clearTimeout(timer.current);
    timer.current = setTimeout(() => onChange({ q: value.trim() }), SEARCH_DELAY_MS);
  }

  function clear() {
    clearTimeout(timer.current);
    setSearch("");
    onChange({
      q: "",
      type: "",
      account_id: "",
      category_id: "",
      party_id: "",
      project_id: "",
      tag: "",
      from: "",
      to: "",
    });
  }

  const categoryList = categories.data ?? [];
  const hasFilters = Object.values(filters).some(Boolean);
  const select = (name: keyof TransactionFilters) => ({
    id: `filter-${name}`,
    value: filters[name] ?? "",
    onChange: (e: React.ChangeEvent<HTMLSelectElement | HTMLInputElement>) =>
      onChange({ [name]: e.target.value }),
  });

  return (
    <div className="flex flex-col gap-3">
      <div className="relative">
        <Search className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          type="search"
          value={search}
          onChange={(e) => onSearchChange(e.target.value)}
          placeholder="Search notes"
          aria-label="Search notes"
          autoComplete="off"
          className="h-11 pl-9"
        />
      </div>

      <div className="grid grid-cols-2 gap-3 md:grid-cols-4 lg:grid-cols-8">
        <Field label="Type" htmlFor="filter-type">
          <NativeSelect {...select("type")}>
            <option value="">All types</option>
            {TYPES.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </NativeSelect>
        </Field>

        <Field label="Account" htmlFor="filter-account_id">
          <NativeSelect {...select("account_id")}>
            <option value="">All accounts</option>
            {(accounts.data ?? []).map((a) => (
              <option key={a.id} value={a.id}>
                {a.name}
              </option>
            ))}
          </NativeSelect>
        </Field>

        <Field label="Category" htmlFor="filter-category_id">
          <NativeSelect {...select("category_id")}>
            <option value="">All categories</option>
            {categoryItems(categoryList, "expense", { includeHidden: true }).map((c) => (
              <option key={c.id} value={c.id}>
                {c.label}
              </option>
            ))}
          </NativeSelect>
        </Field>

        <Field label="Party" htmlFor="filter-party_id">
          <NativeSelect {...select("party_id")}>
            <option value="">All parties</option>
            {(parties.data ?? []).map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </NativeSelect>
        </Field>

        <Field label="Project" htmlFor="filter-project_id">
          <NativeSelect {...select("project_id")}>
            <option value="">All projects</option>
            {(projects.data ?? []).map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </NativeSelect>
        </Field>

        <Field label="Tag" htmlFor="filter-tag">
          <NativeSelect {...select("tag")}>
            <option value="">All tags</option>
            {(tags.data ?? []).map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </NativeSelect>
        </Field>

        <Field label="From" htmlFor="filter-from">
          <Input type="date" max={filters.to || undefined} className="h-11" {...select("from")} />
        </Field>

        <Field label="To" htmlFor="filter-to">
          <Input type="date" min={filters.from || undefined} className="h-11" {...select("to")} />
        </Field>
      </div>

      {hasFilters && (
        <div>
          <Button type="button" variant="outline" className="h-11" onClick={clear}>
            Clear filters
          </Button>
        </div>
      )}
    </div>
  );
}
