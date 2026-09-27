"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { getSupabaseBrowserClient } from "@/lib/supabase/browser";

export function SignInForm() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      const { error: authError } =
        await getSupabaseBrowserClient().auth.signInWithPassword({
          email,
          password,
        });
      if (authError) throw authError;
      router.replace("/workspace");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể đăng nhập.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="mt-8 space-y-5" onSubmit={handleSubmit}>
      <label className="block text-sm font-medium text-slate-700">
        Email
        <input
          className="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2 outline-teal-700"
          autoComplete="email"
          type="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          required
        />
      </label>
      <label className="block text-sm font-medium text-slate-700">
        Mật khẩu
        <input
          className="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2 outline-teal-700"
          autoComplete="current-password"
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          required
        />
      </label>
      {error && (
        <p role="alert" className="text-sm text-red-700">
          {error}
        </p>
      )}
      <button
        className="w-full rounded-lg bg-teal-700 px-4 py-2 font-semibold text-white hover:bg-teal-800 disabled:opacity-60"
        disabled={submitting}
        type="submit"
      >
        {submitting ? "Đang đăng nhập..." : "Đăng nhập"}
      </button>
    </form>
  );
}
