"use client";

import { useHealth } from "@/lib/queries/health";

/** Placeholder for U0: proves browser → Next.js proxy → API → database works. */
export function HealthStatus() {
  const { data, isPending, isError } = useHealth();

  if (isPending) {
    return <p className="text-muted-foreground">Checking the API…</p>;
  }

  if (isError || data.status !== "ok") {
    return (
      <p className="text-destructive" role="alert">
        API unreachable or unhealthy
        {data ? ` (database: ${data.db})` : ""}
      </p>
    );
  }

  return (
    <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2">
      <dt className="text-muted-foreground">API</dt>
      <dd className="font-medium text-success">{data.status}</dd>
      <dt className="text-muted-foreground">Database</dt>
      <dd className="font-medium text-success">{data.db}</dd>
    </dl>
  );
}
