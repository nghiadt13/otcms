"use client";

import { useRef, useState, type ChangeEvent } from "react";
import { toast } from "sonner";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";
import { PageHeader } from "@/components/PageHeader";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { changeReceptionPassword } from "../api/receptionApi";
import { useReception } from "./ReceptionProvider";
import { FormModal, InfoRow, TextField } from "./ReceptionUi";

function PasswordDialog({ onClose }: { onClose: () => void }) {
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const lock = useRef(false);
  return (
    <FormModal
      title="Đổi mật khẩu"
      description="Nhập mật khẩu hiện tại. Mật khẩu mới cần ít nhất 8 ký tự, có chữ và số."
      onClose={() => {
        if (!saving) onClose();
      }}
    >
      <form
        className="space-y-4"
        onSubmit={async (e) => {
          e.preventDefault();
          if (lock.current) return;
          setError("");
          if (!current || next !== confirm || !confirm) {
            setError(formatAppMessage("MSG-08"));
            return;
          }
          lock.current = true;
          setSaving(true);
          try {
            await changeReceptionPassword(current, next);
            setCurrent("");
            setNext("");
            setConfirm("");
            toast.success(formatAppMessage("MSG-07"));
            onClose();
          } catch (e) {
            setError(messageFromError(e));
          } finally {
            setSaving(false);
            lock.current = false;
          }
        }}
      >
        <TextField
          label="Mật khẩu hiện tại"
          type="password"
          value={current}
          onChange={setCurrent}
          autoComplete="current-password"
          disabled={saving}
        />
        <TextField
          label="Mật khẩu mới"
          type="password"
          value={next}
          onChange={setNext}
          autoComplete="new-password"
          disabled={saving}
        />
        <TextField
          label="Nhập lại mật khẩu mới"
          type="password"
          value={confirm}
          onChange={setConfirm}
          autoComplete="new-password"
          disabled={saving}
        />
        {error && (
          <p role="alert" className="text-destructive">
            {error}
          </p>
        )}
        <div className="flex justify-end gap-2 border-t pt-4">
          <Button
            variant="outline"
            type="button"
            onClick={onClose}
            disabled={saving}
          >
            Hủy
          </Button>
          <Button type="submit" disabled={saving}>
            {saving ? "Đang lưu..." : "Đổi mật khẩu"}
          </Button>
        </div>
      </form>
    </FormModal>
  );
}
export function AccountScreen() {
  const { data: s, run, pending } = useReception();
  const [password, setPassword] = useState(false);
  const [error, setError] = useState("");
  const [reading, setReading] = useState(false);
  async function chooseAvatar(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    setError("");
    if (
      !["image/png", "image/jpeg", "image/webp"].includes(file.type) ||
      file.size > 2 * 1024 * 1024 ||
      file.size === 0
    ) {
      setError(formatAppMessage("MSG-08"));
      return;
    }
    setReading(true);
    try {
      const bitmap = await createImageBitmap(file);
      bitmap.close();
      const dataUrl = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result));
        reader.onerror = reject;
        reader.readAsDataURL(file);
      });
      await run({ type: "avatar", dataUrl });
    } catch {
      setError(formatAppMessage("MSG-08"));
    } finally {
      setReading(false);
    }
  }
  return (
    <>
      <PageHeader
        title="Tài khoản của tôi"
        description="Thông tin đăng nhập và ảnh đại diện của tài khoản lễ tân."
        actions={
          <Button onClick={() => setPassword(true)}>Đổi mật khẩu</Button>
        }
      />
      <div className="max-w-3xl space-y-6 rounded-lg border bg-card p-5">
        <div className="flex flex-wrap items-center gap-5">
          <Avatar className="size-16">
            <AvatarImage
              src={s!.account.avatar ?? undefined}
              alt={`Ảnh đại diện ${s!.account.name}`}
            />
            <AvatarFallback>TQ</AvatarFallback>
          </Avatar>
          <div className="min-w-0 flex-1 space-y-2">
            <Label htmlFor="reception-avatar">Đổi ảnh đại diện</Label>
            <Input
              id="reception-avatar"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              disabled={pending || reading}
              onChange={(e) => void chooseAvatar(e)}
              className="max-w-sm"
            />
            <p className="text-xs text-muted-foreground">
              JPG, PNG hoặc WebP · tối đa 2 MB
            </p>
            {s!.account.avatar && (
              <Button
                variant="outline"
                size="sm"
                disabled={pending || reading}
                onClick={() => void run({ type: "avatar", dataUrl: null })}
              >
                Gỡ ảnh
              </Button>
            )}
          </div>
        </div>
        {error && (
          <p role="alert" className="text-destructive">
            {error}
          </p>
        )}
        <dl>
          <InfoRow label="Họ tên">{s!.account.name}</InfoRow>
          <InfoRow label="Tên đăng nhập">{s!.account.username}</InfoRow>
          <InfoRow label="Vai trò">Lễ tân</InfoRow>
          <InfoRow label="Số điện thoại">{s!.account.phone}</InfoRow>
          <InfoRow label="Email công việc">{s!.account.email}</InfoRow>
        </dl>
        <p className="text-muted-foreground">
          Thông tin nhân viên do quản trị viên cập nhật. Bạn có thể đổi ảnh đại
          diện và mật khẩu của mình.
        </p>
      </div>
      <p className="mt-4 text-xs text-muted-foreground">
        Tài khoản mẫu dùng để review giao diện. Mật khẩu ban đầu: Demo12345.
        Thay đổi tại đây không tác động tài khoản Supabase.
      </p>
      {password && <PasswordDialog onClose={() => setPassword(false)} />}
    </>
  );
}
