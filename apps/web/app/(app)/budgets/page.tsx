import { Suspense } from "react";
import { BudgetsView } from "@/components/budgets/budgets-view";

export const metadata = { title: "Budgets · Hisaab" };

// BudgetsView reads the ?period= search param, which needs a Suspense boundary.
export default function BudgetsPage() {
  return (
    <Suspense>
      <BudgetsView />
    </Suspense>
  );
}
