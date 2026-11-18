import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";
import { ME_KEY, type Me } from "./me";

type LoginRequest = components["schemas"]["LoginRequest"];
type SignupRequest = components["schemas"]["SignupRequest"];

// Login and signup answer with the user's profile (the tokens arrive as httpOnly
// cookies), so it goes straight into the cache and the shell needs no second request.
export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: LoginRequest) =>
      apiFetch<Me>("/api/v1/auth/login", { method: "POST", json: body }),
    onSuccess: (me) => queryClient.setQueryData(ME_KEY, me),
  });
}

export function useSignup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: SignupRequest) =>
      apiFetch<Me>("/api/v1/auth/signup", { method: "POST", json: body }),
    onSuccess: (me) => queryClient.setQueryData(ME_KEY, me),
  });
}

export function useLogout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch<void>("/api/v1/auth/logout", { method: "POST" }),
    // Drop every cached query so the next person on this browser sees none of it.
    onSettled: () => queryClient.clear(),
  });
}
