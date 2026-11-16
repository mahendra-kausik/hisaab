import { HealthStatus } from "@/components/health/health-status";

export default function Home() {
  return (
    <main className="mx-auto flex w-full max-w-[1100px] flex-1 flex-col justify-center gap-6 px-4 py-12 md:px-8">
      <h1 className="text-3xl font-semibold">Hisaab</h1>
      <section className="max-w-md rounded-xl border bg-card p-6">
        <h2 className="mb-4 text-base font-medium">System status</h2>
        <HealthStatus />
      </section>
    </main>
  );
}
