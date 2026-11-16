"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState } from "react";

/** Gives every client component access to TanStack Query's shared cache. */
export function Providers({ children }: { children: React.ReactNode }) {
  // useState keeps one QueryClient for the lifetime of the browser tab.
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: { queries: { staleTime: 30_000, retry: 1 } },
      }),
  );

  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}
