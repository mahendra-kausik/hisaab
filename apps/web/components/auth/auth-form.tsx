"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiError } from "@/lib/api/client";
import { useLogin, useSignup } from "@/lib/queries/auth";
import { safeNext } from "@/lib/safe-redirect";

// These mirror the API's rules (SignupRequest / LoginRequest) so most mistakes are caught
// before a request is made. The API still validates everything again.
const email = z.string().trim().min(1, "Enter your email").pipe(z.email("Enter a valid email"));
// bcrypt only reads the first 72 bytes, so the API limits bytes, not characters.
const newPassword = z
  .string()
  .min(8, "Use at least 8 characters")
  .refine((v) => new TextEncoder().encode(v).length <= 72, "Use at most 72 bytes");

const loginSchema = z.object({
  email,
  password: z.string().min(1, "Enter your password"),
});
const signupSchema = z.object({
  name: z.string().trim().min(1, "Enter your name").max(100, "Use at most 100 characters"),
  email,
  password: newPassword,
});

type Mode = "login" | "signup";
type FieldErrors = Partial<Record<"name" | "email" | "password", string>>;

const COPY = {
  login: {
    title: "Welcome back",
    submit: "Log in",
    switchText: "New here?",
    switchLink: "Create an account",
    switchHref: "/signup",
    passwordAutocomplete: "current-password",
  },
  signup: {
    title: "Create your account",
    submit: "Sign up",
    switchText: "Already have an account?",
    switchLink: "Log in",
    switchHref: "/login",
    passwordAutocomplete: "new-password",
  },
} as const;

export function AuthForm({ mode }: { mode: Mode }) {
  const copy = COPY[mode];
  const router = useRouter();
  const next = safeNext(useSearchParams().get("next"));
  const login = useLogin();
  const signup = useSignup();
  const mutation = mode === "login" ? login : signup;

  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);

  function onSubmit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);

    const data = Object.fromEntries(new FormData(event.currentTarget));
    const parsed = (mode === "login" ? loginSchema : signupSchema).safeParse(data);
    if (!parsed.success) {
      const errors: FieldErrors = {};
      for (const issue of parsed.error.issues) {
        const field = issue.path[0] as keyof FieldErrors;
        errors[field] ??= issue.message;
      }
      setFieldErrors(errors);
      return;
    }
    setFieldErrors({});

    const options = {
      onSuccess: () => router.replace(next ?? "/"),
      onError: (error: Error) =>
        setFormError(error instanceof ApiError ? error.message : "Something went wrong. Try again."),
    };
    if (mode === "login") {
      login.mutate(loginSchema.parse(data), options);
    } else {
      signup.mutate(signupSchema.parse(data), options);
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">
      <h1 className="text-2xl font-semibold">{copy.title}</h1>

      {mode === "signup" && (
        <Field id="name" label="Name" error={fieldErrors.name}>
          <Input
            id="name"
            name="name"
            autoComplete="name"
            autoFocus
            className="h-11"
            {...invalid("name", fieldErrors.name)}
          />
        </Field>
      )}
      <Field id="email" label="Email" error={fieldErrors.email}>
        <Input
          id="email"
          name="email"
          type="email"
          autoComplete="email"
          autoFocus={mode === "login"}
          className="h-11"
          {...invalid("email", fieldErrors.email)}
        />
      </Field>
      <Field id="password" label="Password" error={fieldErrors.password}>
        <Input
          id="password"
          name="password"
          type="password"
          autoComplete={copy.passwordAutocomplete}
          className="h-11"
          {...invalid("password", fieldErrors.password)}
        />
      </Field>

      {formError && (
        <p role="alert" className="text-sm text-destructive">
          {formError}
        </p>
      )}

      <Button type="submit" disabled={mutation.isPending} className="h-11">
        {mutation.isPending ? "Please wait…" : copy.submit}
      </Button>

      <p className="text-center text-sm text-muted-foreground">
        {copy.switchText}{" "}
        <Link
          href={next ? `${copy.switchHref}?next=${encodeURIComponent(next)}` : copy.switchHref}
          className="font-medium text-primary underline-offset-4 hover:underline"
        >
          {copy.switchLink}
        </Link>
      </p>
    </form>
  );
}

// Lets screen readers announce the error text with the field.
function invalid(id: string, error?: string) {
  return { "aria-invalid": error ? true : undefined, "aria-describedby": error ? `${id}-error` : undefined };
}

function Field({
  id,
  label,
  error,
  children,
}: {
  id: string;
  label: string;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {error && (
        <p className="text-sm text-destructive" id={`${id}-error`}>
          {error}
        </p>
      )}
    </div>
  );
}
