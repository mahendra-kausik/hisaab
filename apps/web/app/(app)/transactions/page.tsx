import { Suspense } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { TransactionsView } from "@/components/transactions/transactions-view";

export const metadata = { title: "Transactions · Hisaab" };

export default function TransactionsPage() {
  return (
    <>
      <h1 className="mb-6 text-2xl font-semibold">Transactions</h1>
      {/* useSearchParams needs a Suspense boundary above it. */}
      <Suspense fallback={<Skeleton className="h-40 w-full" />}>
        <TransactionsView />
      </Suspense>
    </>
  );
}
