"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card } from "@/components/ui/card";
import { OtcmsLogo } from "@/components/brand/OtcmsLogo";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useState, type FormEvent } from "react";
import { signInWithPassword, signOut } from "@/lib/auth/authSession";
import { Icon } from "./Icon";

export function StaffLoginScreen() {
  const router = useRouter();
  const [error, setError] = useState("");
  const [show, setShow] = useState(false);
  const [loading, setLoading] = useState(false);
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    const user = String(data.get("username")).trim();
    const password = String(data.get("password"));
    if (!user || !password) return setError("Nhập email hoặc tên đăng nhập và mật khẩu.");
    setError("");
    setLoading(true);
    try {
      const session = await signInWithPassword("staff", user, password);
      if (session.accountType !== "IMAGING_TECHNICIAN") {
        await signOut();
        setError("Tài khoản này không thuộc kỹ thuật viên chẩn đoán hình ảnh.");
        return;
      }
      router.replace(session.mustChangePassword ? "/imaging/change-password" : "/imaging");
    } catch {
      setError("Không thể đăng nhập bằng thông tin này. Vui lòng kiểm tra và thử lại.");
    } finally {
      setLoading(false);
    }
  }
  return (
    <main className="flex min-h-dvh flex-col items-center justify-center gap-6 bg-background px-4 py-8">
      <OtcmsLogo tone="onLight" />
      <Card className="w-full max-w-lg gap-0 rounded-xl p-6 sm:p-8">
        <h1 className="text-3xl font-semibold tracking-tight">
          Đăng nhập nhân viên
        </h1>
        <p className="mt-2 text-base text-muted-foreground">
          Khu vực kỹ thuật viên chẩn đoán hình ảnh.
        </p>
        <form onSubmit={submit} className="mt-6 space-y-5">
          <label className="im-field">
            Email hoặc tên đăng nhập
            <Input
              className="h-12 text-base"
              name="username"
              autoComplete="username"
              required
              placeholder="Nhập email hoặc tên đăng nhập"
            />
          </label>
          <label className="im-field">
            Mật khẩu
            <div className="im-password-input">
              <Input
                className="h-12 text-base"
                name="password"
                aria-label="Mật khẩu"
                autoComplete="current-password"
                required
                type={show ? "text" : "password"}
                placeholder="Nhập mật khẩu"
              />
              <Button
                variant="ghost"
                type="button"
                className="im-text-button"
                onClick={() => setShow(!show)}
              >
                {show ? "Ẩn" : "Hiện"}
              </Button>
            </div>
          </label>
          {error && (
            <p className="text-sm text-destructive" role="alert">
              {error}
            </p>
          )}
          <Button
            className="im-button h-12 w-full text-base"
            disabled={loading}
            type="submit"
          >
            {loading ? "Đang đăng nhập…" : "Đăng nhập"}
            <Icon name="arrow" />
          </Button>
        </form>
        <p className="mt-5 text-sm text-muted-foreground">
          <Link href="/forgot-password?portal=staff" className="underline">Quên mật khẩu?</Link>
        </p>
      </Card>
    </main>
  );
}
