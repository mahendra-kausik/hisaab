import { Suspense } from "react";
import { AppShell } from "@/components/shell/app-shell";

// The shell reads the pathname, which is only known at request time on a dynamic route such as
// /parties/[partyId]. The Suspense boundary lets those routes build; the shell renders its own
// skeleton until `GET /me` answers, so there is nothing useful to show in the fallback.
export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <Suspense fallback={null}>
      <AppShell>{children}</AppShell>
    </Suspense>
  );
}
