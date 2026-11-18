"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState } from "react";
import { Toaster } from "@/components/ui/sonner";
import { ApiError } from "@/lib/api/client";

/** Gives every client component access to TanStack Query's shared cache. */
export function Providers({ children }: { children: React.ReactNode }) {
  // useState keeps one QueryClient for the lifetime of the browser tab.
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            // A 4xx answer (such as 401 "not signed in") will not change on retry.
            retry: (failureCount, error) =>
              !(error instanceof ApiError && error.status < 500) && failureCount < 1,
          },
        },
      }),
  );

  return (
    <QueryClientProvider client={queryClient}>
      {children}
      <Toaster />
    </QueryClientProvider>
  );
}
