"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { apiRequest } from "@/lib/api/client";
import {
  ACCOUNT_AVATAR_CHANGED_EVENT,
  loadAccountAvatarUrl,
  notifyAccountAvatarChanged,
} from "@/lib/auth/accountAvatarCache";
import {
  fetchCurrentAccount,
  resetCurrentAccountCache,
  type CurrentAccount,
} from "@/lib/auth/currentAccountApi";
import { messageFromError } from "@/lib/messages/appMessages";
import { ActionLink } from "./ActionLink";
import { Badge } from "./Badge";
import { Icon } from "./Icon";
import { Info } from "./Info";
import { PageHeading } from "./PageHeading";
import { Panel } from "./Panel";

export function ProfileScreen({ edit = false }: { edit?: boolean }) {
  const router = useRouter();
  const input = useRef<HTMLInputElement>(null);
  const previewUrlRef = useRef<string | null>(null);
  const [account, setAccount] = useState<CurrentAccount | null>(null);
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    const load = async () => {
      try {
        const current = await fetchCurrentAccount();
        if (!current) {
          if (active) setError("Không tìm thấy tài khoản đang đăng nhập.");
          return;
        }
        const image = current.avatarUrl
          ? await loadAccountAvatarUrl(current.id)
          : null;
        if (active) {
          setAccount(current);
          setAvatarUrl(image);
        }
      } catch (cause) {
        if (active) setError(messageFromError(cause));
      } finally {
        if (active) setLoading(false);
      }
    };
    void load();
    window.addEventListener(ACCOUNT_AVATAR_CHANGED_EVENT, load);
    return () => {
      active = false;
      window.removeEventListener(ACCOUNT_AVATAR_CHANGED_EVENT, load);
    };
  }, []);

  useEffect(
    () => () => {
      if (previewUrlRef.current) URL.revokeObjectURL(previewUrlRef.current);
    },
    [],
  );

  function chooseFile(selected: File | null) {
    if (!selected) return;
    if (
      !["image/png", "image/jpeg", "image/webp"].includes(selected.type) ||
      selected.size > 2 * 1024 * 1024
    ) {
      if (previewUrlRef.current) URL.revokeObjectURL(previewUrlRef.current);
      previewUrlRef.current = null;
      setPreviewUrl(null);
      setFile(null);
      if (input.current) input.current.value = "";
      setError("Chọn ảnh JPG, PNG hoặc WebP, tối đa 2 MB.");
      return;
    }
    if (previewUrlRef.current) URL.revokeObjectURL(previewUrlRef.current);
    const url = URL.createObjectURL(selected);
    previewUrlRef.current = url;
    setPreviewUrl(url);
    setFile(selected);
    setError("");
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    if (!file) {
      router.push("/imaging/profile");
      return;
    }
    setSaving(true);
    setError("");
    try {
      const form = new FormData();
      form.append("photo", file);
      await apiRequest("/api/v1/me/avatar", { method: "PUT", body: form });
      resetCurrentAccountCache();
      notifyAccountAvatarChanged();
      router.push("/imaging/profile");
    } catch (cause) {
      setError(messageFromError(cause));
    } finally {
      setSaving(false);
    }
  }

  async function removeAvatar() {
    setSaving(true);
    setError("");
    try {
      await apiRequest("/api/v1/me/avatar", { method: "DELETE" });
      resetCurrentAccountCache();
      notifyAccountAvatarChanged();
      setAvatarUrl(null);
      setFile(null);
    } catch (cause) {
      setError(messageFromError(cause));
    } finally {
      setSaving(false);
    }
  }

  const name = account?.displayName || account?.username || "Tài khoản";
  const shownAvatar = previewUrl ?? avatarUrl;

  if (loading)
    return <p role="status">Đang tải thông tin tài khoản…</p>;

  return (
    <>
      <PageHeading
        eyebrow="CÁ NHÂN"
        title={edit ? "Cập nhật ảnh đại diện" : "Tài khoản của tôi"}
        description="Thông tin tài khoản được tải từ hệ thống."
      >
        {!edit && (
          <ActionLink secondary href="/imaging/profile/edit">
            <Icon name="user" />
            Cập nhật ảnh đại diện
          </ActionLink>
        )}
      </PageHeading>
      {error && <p role="alert" className="im-form-error">{error}</p>}
      {account ? (
        <form onSubmit={(event) => void save(event)}>
          <div className="im-account-layout">
            <Panel>
              <div className="im-profile-card">
                <div className="im-profile-avatar">
                  {shownAvatar ? (
                    <Image
                      src={shownAvatar}
                      alt="Ảnh đại diện tài khoản"
                      width={108}
                      height={108}
                      unoptimized
                    />
                  ) : (
                    <span>{name.slice(0, 2).toUpperCase()}</span>
                  )}
                </div>
                <h2>{name}</h2>
                <p>Kỹ thuật viên chẩn đoán hình ảnh</p>
                {account.status && (
                  <Badge tone={account.status === "ACTIVE" ? "green" : "amber"}>
                    {account.status === "ACTIVE" ? "Đang hoạt động" : "Đã khóa"}
                  </Badge>
                )}
                {edit && (
                  <>
                    <Input
                      ref={input}
                      aria-label="Chọn ảnh đại diện"
                      className="im-sr-only"
                      type="file"
                      accept="image/png,image/jpeg,image/webp"
                      onChange={(event) => {
                        chooseFile(event.target.files?.[0] ?? null);
                      }}
                    />
                    <Button
                      variant="outline"
                      className="im-button im-button-secondary"
                      type="button"
                      onClick={() => input.current?.click()}
                    >
                      <Icon name="upload" />
                      Chọn ảnh đại diện
                    </Button>
                    <small>JPG, PNG hoặc WebP · Tối đa 2 MB</small>
                    {avatarUrl && !file && (
                      <Button
                        variant="ghost"
                        type="button"
                        disabled={saving}
                        className="im-text-button im-danger"
                        onClick={() => void removeAvatar()}
                      >
                        Xóa ảnh đại diện
                      </Button>
                    )}
                  </>
                )}
              </div>
            </Panel>
            <Panel title="Thông tin tài khoản">
              <dl className="im-details">
                <div>
                  <dt>Tên đăng nhập</dt>
                  <dd>{account.username || "—"}</dd>
                </div>
                <div>
                  <dt>Họ và tên</dt>
                  <dd>{account.displayName || "—"}</dd>
                </div>
                <div>
                  <dt>Email</dt>
                  <dd>{account.email}</dd>
                </div>
                <div>
                  <dt>Vai trò</dt>
                  <dd>Kỹ thuật viên chẩn đoán hình ảnh</dd>
                </div>
              </dl>
              <div className="im-panel-body">
                <Info>
                  Hồ sơ nhân viên do Administrator quản lý. Bạn có thể cập nhật
                  ảnh đại diện và đổi mật khẩu của tài khoản.
                </Info>
                {!edit && (
                  <ActionLink secondary href="/imaging/change-password">
                    <Icon name="lock" />
                    Đổi mật khẩu
                  </ActionLink>
                )}
              </div>
            </Panel>
          </div>
          {edit && (
            <div className="im-action-footer">
              <ActionLink secondary href="/imaging/profile">Hủy</ActionLink>
              <Button className="im-button" type="submit" disabled={saving}>
                {saving ? "Đang lưu…" : "Lưu thay đổi"}
              </Button>
            </div>
          )}
        </form>
      ) : null}
    </>
  );
}
