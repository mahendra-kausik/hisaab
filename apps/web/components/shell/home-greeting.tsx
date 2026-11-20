"use client";

import { DuesBanner } from "@/components/dues/dues-banner";
import { useMe } from "@/lib/queries/me";
import { useWorkspace } from "@/lib/workspace";

/** Placeholder Home content; real summaries arrive with the transactions and reports units. */
export function HomeGreeting() {
  const { data: me } = useMe();
  const { active } = useWorkspace();

  return (
    <div className="flex flex-col gap-6">
      <DuesBanner />
      <section className="flex flex-col gap-2">
        <h1 className="text-3xl font-semibold">Hi, {me?.name}</h1>
        <p className="text-muted-foreground">
          You are working in <span className="font-medium text-foreground">{active.name}</span>.
        </p>
      </section>
    </div>
  );
}
