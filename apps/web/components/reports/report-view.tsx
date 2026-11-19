"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Bar, BarChart, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { periodLabel, shiftDay } from "@/lib/budgets";
import { todayIn } from "@/lib/dates";
import { formatINR, formatINRCompact } from "@/lib/format";
import { useMe } from "@/lib/queries/me";
import { useMonthlyReport, type MonthlyReport } from "@/lib/queries/reports";
import { percentChange } from "@/lib/reports";
import { cn } from "@/lib/utils";
import { useWorkspace } from "@/lib/workspace";

// Slices need distinct colours and the theme has no chart tokens; these read on light and dark.
const PALETTE = ["#0d9488", "#f59e0b", "#6366f1", "#ec4899", "#84cc16", "#06b6d4", "#f97316", "#8b5cf6"];

function Comparison({ current, previous }: { current: number; previous: number }) {
  const pct = percentChange(current, previous);
  if (pct === null) return <span>Nothing in the previous period</span>;
  if (pct === 0) return <span>Same as the previous period</span>;
  return <span>{`${pct > 0 ? "+" : "−"}${Math.abs(pct)}% vs previous period`}</span>;
}

function StatCard({
  label,
  paise,
  previousPaise,
  tone,
}: {
  label: string;
  paise: number;
  previousPaise: number;
  tone?: string;
}) {
  return (
    <div className="flex flex-col gap-1 rounded-xl border bg-surface p-4">
      <span className="text-sm text-muted-foreground">{label}</span>
      <span className={cn("text-3xl font-semibold tabular-nums", tone)}>{formatINR(paise)}</span>
      <span className="text-sm text-muted-foreground">
        <Comparison current={paise} previous={previousPaise} />
      </span>
    </div>
  );
}

function CategorySection({ report }: { report: MonthlyReport }) {
  const total = report.totals.expense_paise;
  if (report.by_category.length === 0) return null;
  return (
    <section aria-labelledby="by-category" className="flex flex-col gap-3">
      <h2 id="by-category" className="px-1 text-lg font-semibold">
        Spend by category
      </h2>
      <div className="flex flex-col gap-4 rounded-xl border bg-surface p-4 md:flex-row md:items-center">
        <div className="h-56 w-full md:w-64 md:shrink-0" role="img" aria-label="Donut chart of spend by category">
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie
                data={report.by_category}
                dataKey="spent_paise"
                nameKey="category_name"
                innerRadius="60%"
                outerRadius="100%"
                stroke="none"
                isAnimationActive={false}
              >
                {report.by_category.map((c, i) => (
                  <Cell key={c.category_id ?? "none"} fill={PALETTE[i % PALETTE.length]} />
                ))}
              </Pie>
              <Tooltip formatter={(value) => formatINR(Number(value))} />
            </PieChart>
          </ResponsiveContainer>
        </div>
        <table className="w-full text-sm">
          <caption className="sr-only">Spend by category</caption>
          <tbody className="divide-y">
            {report.by_category.map((c, i) => (
              <tr key={c.category_id ?? "none"}>
                <td className="py-2 pr-2">
                  <span
                    aria-hidden
                    className="mr-2 inline-block size-3 rounded-sm align-middle"
                    style={{ background: PALETTE[i % PALETTE.length] }}
                  />
                  {c.category_name}
                </td>
                <td className="py-2 pr-2 text-right text-muted-foreground tabular-nums">
                  {Math.round((c.spent_paise * 100) / total)}%
                </td>
                <td className="py-2 text-right font-medium tabular-nums">{formatINR(c.spent_paise)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function DailySection({ report }: { report: MonthlyReport }) {
  if (report.totals.expense_paise === 0) return null;
  return (
    <section aria-labelledby="daily" className="flex flex-col gap-3">
      <h2 id="daily" className="px-1 text-lg font-semibold">
        Daily spend
      </h2>
      <div
        className="h-56 rounded-xl border bg-surface p-4"
        role="img"
        aria-label="Bar chart of spend for each day of the period"
      >
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={report.daily} margin={{ top: 4, right: 0, bottom: 0, left: 0 }}>
            {/* "2026-11-05" → "5": the day of the month. */}
            <XAxis dataKey="day" tickFormatter={(d: string) => String(Number(d.slice(8)))} interval="preserveStartEnd" />
            <YAxis width={48} tickFormatter={(v) => formatINRCompact(Number(v))} />
            <Tooltip
              formatter={(value) => formatINR(Number(value))}
              labelFormatter={(d) => periodLabel(String(d), String(d)).split(" – ")[0]}
            />
            <Bar dataKey="spent_paise" name="Spent" fill={PALETTE[0]} radius={[3, 3, 0, 0]} isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </section>
  );
}

function TopSection({ report }: { report: MonthlyReport }) {
  if (report.top_expenses.length === 0) return null;
  return (
    <section aria-labelledby="top" className="flex flex-col gap-3">
      <h2 id="top" className="px-1 text-lg font-semibold">
        Top expenses
      </h2>
      <ol className="divide-y rounded-xl border bg-surface">
        {report.top_expenses.map((t) => (
          <li key={t.id} className="flex items-center justify-between gap-3 p-4 text-sm">
            <div className="flex min-w-0 flex-col">
              <span className="truncate font-medium">{t.note || t.category_name || "Uncategorized"}</span>
              <span className="text-muted-foreground">
                {periodLabel(t.occurred_on, t.occurred_on).split(" – ")[0]}
                {t.note && t.category_name ? ` · ${t.category_name}` : ""}
              </span>
            </div>
            <span className="font-medium tabular-nums">{formatINR(t.amount_paise)}</span>
          </li>
        ))}
      </ol>
    </section>
  );
}

export function ReportView() {
  const { active } = useWorkspace();
  const router = useRouter();
  const pathname = usePathname();
  const day = useSearchParams().get("period") ?? undefined;
  const me = useMe();
  const query = useMonthlyReport(active.id, day);

  function go(target: string | undefined) {
    router.replace(target ? `${pathname}?period=${target}` : pathname);
  }

  if (query.isError) {
    return (
      <p role="alert" className="text-sm text-destructive">
        Could not load the report.
      </p>
    );
  }
  if (!query.data || !me.data) return <Skeleton className="h-64 w-full rounded-xl" />;

  const report = query.data;
  const { period_start: start, period_end: end, totals, previous } = report;
  const today = todayIn(me.data.timezone);
  const isCurrent = start <= today && today <= end;
  const empty = totals.income_paise === 0 && totals.expense_paise === 0;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">Reports</h1>
        <div className="flex items-center gap-1">
          <Button variant="outline" size="icon" className="size-11" onClick={() => go(shiftDay(start, -1))}>
            <ChevronLeft className="h-4 w-4" />
            <span className="sr-only">Previous period</span>
          </Button>
          <span className="min-w-44 text-center text-sm font-medium">{periodLabel(start, end)}</span>
          <Button
            variant="outline"
            size="icon"
            className="size-11"
            disabled={isCurrent}
            onClick={() => go(shiftDay(end, 1))}
          >
            <ChevronRight className="h-4 w-4" />
            <span className="sr-only">Next period</span>
          </Button>
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-3">
        <StatCard label="Income" paise={totals.income_paise} previousPaise={previous.income_paise} tone="text-money-in" />
        <StatCard label="Expense" paise={totals.expense_paise} previousPaise={previous.expense_paise} tone="text-money-out" />
        <StatCard label="Net" paise={totals.net_paise} previousPaise={previous.net_paise} />
      </div>

      {empty ? (
        <p className="text-sm text-muted-foreground">No income or expenses in this period.</p>
      ) : (
        <>
          <CategorySection report={report} />
          <DailySection report={report} />
          <TopSection report={report} />
        </>
      )}
    </div>
  );
}
