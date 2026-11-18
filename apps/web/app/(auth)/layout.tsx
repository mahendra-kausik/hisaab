export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <main className="flex flex-1 items-center justify-center px-4 py-12">
      <div className="w-full max-w-sm rounded-2xl border bg-card p-6 md:p-8">
        <p className="mb-6 text-sm font-semibold tracking-wide text-primary">Hisaab</p>
        {children}
      </div>
    </main>
  );
}
