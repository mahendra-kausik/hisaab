import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";

export type MonthlyReport = components["schemas"]["MonthlyReport"];

/** Report for the period containing `day` ("YYYY-MM-DD"); undefined means the current period. */
export function useMonthlyReport(workspaceId: string, day?: string) {
  return useQuery({
    queryKey: ["reports", workspaceId, "monthly", day ?? "current"],
    queryFn: () =>
      apiFetch<MonthlyReport>(
        `/api/v1/workspaces/${workspaceId}/reports/monthly${day ? `?period=${day}` : ""}`,
      ),
  });
}
