import { Suspense } from "react";
import { ReportView } from "@/components/reports/report-view";

export const metadata = { title: "Reports · Hisaab" };

// ReportView reads the ?period= search param, which needs a Suspense boundary.
export default function ReportsPage() {
  return (
    <Suspense>
      <ReportView />
    </Suspense>
  );
}
