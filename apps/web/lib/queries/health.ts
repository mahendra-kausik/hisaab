import { useQuery } from "@tanstack/react-query";
import type { components } from "@/lib/api/schema";

export type Health = components["schemas"]["HealthResponse"];

// Relative path: Next.js proxies /api/* to the API (see next.config.ts).
async function fetchHealth(): Promise<Health> {
  const response = await fetch("/api/v1/health");
  // A failing database answers 503 with a body; anything else is unexpected.
  if (!response.ok && response.status !== 503) {
    throw new Error(`Health check failed with status ${response.status}`);
  }
  return (await response.json()) as Health;
}

export function useHealth() {
  return useQuery({ queryKey: ["health"], queryFn: fetchHealth });
}
