"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Menu } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetDescription, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiError } from "@/lib/api/client";
import { useMe } from "@/lib/queries/me";
import { WorkspaceProvider } from "@/lib/workspace";
import { Sidebar } from "./sidebar";

/**
 * Wraps every signed-in page. The check is made here, in the browser, because the auth
 * cookies are only sent to /api paths, so Next.js never sees them on a page request.
 * `GET /me` answers 401 when there is no valid session (after one automatic refresh try).
 */
export function AppShell({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const me = useMe();
  const [menuOpen, setMenuOpen] = useState(false);

  const signedOut = me.error instanceof ApiError && me.error.status === 401;
  useEffect(() => {
    if (signedOut) router.replace(`/login?next=${encodeURIComponent(pathname)}`);
  }, [signedOut, router, pathname]);

  if (me.error && !signedOut) {
    return (
      <main className="flex flex-1 flex-col items-center justify-center gap-4 p-6 text-center">
        <p>We could not load your account.</p>
        <Button onClick={() => me.refetch()} className="h-11">
          Try again
        </Button>
      </main>
    );
  }

  if (!me.data) return <ShellSkeleton />;

  return (
    <WorkspaceProvider me={me.data}>
      <div className="flex flex-1 flex-col md:flex-row">
        {/* Desktop: fixed 240px sidebar. */}
        <aside className="hidden w-60 shrink-0 border-r bg-surface md:block">
          <div className="sticky top-0 h-screen">
            <Sidebar me={me.data} />
          </div>
        </aside>

        {/* Narrow screens: top bar with a slide-over menu. */}
        <header className="flex h-14 items-center gap-2 border-b bg-surface px-4 md:hidden">
          <Sheet open={menuOpen} onOpenChange={setMenuOpen}>
            <SheetTrigger render={<Button variant="ghost" size="icon" className="size-11" />}>
              <Menu className="h-5 w-5" />
              <span className="sr-only">Open menu</span>
            </SheetTrigger>
            <SheetContent side="left" className="w-72 max-w-[85vw]">
              <SheetTitle className="sr-only">Menu</SheetTitle>
              <SheetDescription className="sr-only">Navigate and switch workspace</SheetDescription>
              <Sidebar me={me.data} onNavigate={() => setMenuOpen(false)} />
            </SheetContent>
          </Sheet>
          <span className="font-semibold text-primary">Hisaab</span>
        </header>

        <main className="mx-auto w-full max-w-[1100px] min-w-0 flex-1 px-4 py-6 md:px-8 md:py-8">
          {children}
        </main>
      </div>
    </WorkspaceProvider>
  );
}

// Skeleton while /me loads (no full-page spinner).
function ShellSkeleton() {
  return (
    <div className="flex flex-1" aria-busy="true">
      <aside className="hidden w-60 shrink-0 flex-col gap-3 border-r bg-surface p-3 md:flex">
        <Skeleton className="h-11 w-full" />
        {Array.from({ length: 6 }, (_, i) => (
          <Skeleton key={i} className="h-11 w-full" />
        ))}
      </aside>
      <div className="flex-1 p-6">
        <Skeleton className="h-8 w-48" />
      </div>
    </div>
  );
}
