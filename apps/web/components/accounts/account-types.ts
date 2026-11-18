import { Banknote, CreditCard, Landmark, Wallet, type LucideIcon } from "lucide-react";
import type { Account } from "@/lib/queries/accounts";

export const ACCOUNT_TYPES: { value: Account["type"]; label: string; icon: LucideIcon }[] = [
  { value: "cash", label: "Cash", icon: Banknote },
  { value: "bank", label: "Bank account", icon: Landmark },
  { value: "credit_card", label: "Credit card", icon: CreditCard },
  { value: "wallet", label: "Wallet", icon: Wallet },
];

export function accountTypeInfo(type: Account["type"]) {
  return ACCOUNT_TYPES.find((t) => t.value === type) ?? ACCOUNT_TYPES[0];
}
