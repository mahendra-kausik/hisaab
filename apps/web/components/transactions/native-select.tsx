import { cn } from "@/lib/utils";

/** A native select: accessible, keyboard friendly and 44px tall, with no extra code. */
export function NativeSelect({ className, ...props }: React.ComponentProps<"select">) {
  return (
    <select
      {...props}
      className={cn(
        "h-11 w-full rounded-md border border-input bg-transparent px-2.5 text-base outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 md:text-sm",
        className,
      )}
    />
  );
}
