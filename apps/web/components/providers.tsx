"use client";

import { MutationCache, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState } from "react";
import { Toaster } from "@/components/ui/sonner";
import { ApiError } from "@/lib/api/client";

/** Gives every client component access to TanStack Query's shared cache. */
export function Providers({ children }: { children: React.ReactNode }) {
  // useState keeps one QueryClient for the lifetime of the browser tab.
  const [queryClient] = useState(() => {
    const client: QueryClient = new QueryClient({
      // After ANY successful save, mark every cached query stale; the ones on screen refetch.
      // One rule instead of each mutation listing what it touches (a new transaction moves
      // budgets, reports, balances, dues...). TanStack awaits the returned promise before the
      // mutation's own onSuccess, so a dialog closes on fresh data. Opt out with meta.skipRefresh.
      mutationCache: new MutationCache({
        onSuccess: (_data, _variables, _context, mutation) =>
          mutation.meta?.skipRefresh ? undefined : client.invalidateQueries(),
      }),
      defaultOptions: {
        queries: {
          staleTime: 30_000,
          // A 4xx answer (such as 401 "not signed in") will not change on retry.
          retry: (failureCount, error) =>
            !(error instanceof ApiError && error.status < 500) && failureCount < 1,
        },
      },
    });
    return client;
  });

  return (
    <QueryClientProvider client={queryClient}>
      {children}
      <Toaster
        offset={{ bottom: "calc(24px + var(--tab-bar-h, 0px))" }}
        mobileOffset={{ bottom: "calc(16px + var(--tab-bar-h, 0px))" }}
      />
    </QueryClientProvider>
  );
}
