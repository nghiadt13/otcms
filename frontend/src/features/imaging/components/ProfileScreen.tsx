"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { useRef, useState, type FormEvent } from "react";
import { useImaging } from "../hooks/useImaging";
import { ActionLink } from "./ActionLink";
import { Badge } from "./Badge";
import { Icon } from "./Icon";
import { Info } from "./Info";
import { PageHeading } from "./PageHeading";
import { Panel } from "./Panel";

export function ProfileScreen({ edit = false }: { edit?: boolean }) {
  const { state, update, notify } = useImaging();
  const router = useRouter();
  const input = useRef<HTMLInputElement>(null);
  const [avatar, setAvatar] = useState(state.profile.avatar);
  const [error, setError] = useState("");
  function save(e: FormEvent) {
    e.preventDefault();
    update((s) => ({ ...s, profile: { ...s.profile, avatar } }));
    notify("Đã cập nhật ảnh đại diện trong bản xem thử.");
    router.push("/imaging/profile");
  }
  async function choose(file: File | undefined) {
    if (!file) return;
    if (
      !["image/png", "image/jpeg", "image/webp"].includes(file.type) ||
      file.size > 2 * 1024 * 1024
    ) {
      setError("Chọn ảnh JPG, PNG hoặc WebP, tối đa 2 MB.");
      return;
    }
    setError("");
    const reader = new FileReader();
    reader.onload = () => setAvatar(String(reader.result));
    reader.onerror = () => setError("Không thể đọc ảnh. Vui lòng chọn lại.");
    reader.readAsDataURL(file);
  }
  return (
    <>
      <PageHeading
        eyebrow="CÁ NHÂN"
        title={edit ? "Cập nhật tài khoản" : "Tài khoản của tôi"}
        description={
          edit
            ? "Thay đổi ảnh đại diện của tài khoản."
            : "Thông tin tài khoản và hồ sơ công việc của bạn."
        }
      >
        {!edit && (
          <ActionLink secondary href="/imaging/profile/edit">
            <Icon name="user" />
            Cập nhật tài khoản
          </ActionLink>
        )}
      </PageHeading>
      <form onSubmit={save}>
        <div className="im-account-layout">
          <Panel>
            <div className="im-profile-card">
              <div className="im-profile-avatar">
                {(edit ? avatar : state.profile.avatar) ? (
                  <Image
                    src={edit ? avatar : state.profile.avatar}
                    alt="Ảnh đại diện kỹ thuật viên"
                    width={108}
                    height={108}
                    unoptimized
                  />
                ) : (
                  <span>MA</span>
                )}
              </div>
              <h2>{state.profile.name}</h2>
              <p>Kỹ thuật viên chẩn đoán hình ảnh</p>
              <Badge tone="green">Tài khoản đang hoạt động</Badge>
              {edit && (
                <>
                  <Input
                    ref={input}
                    aria-label="Chọn ảnh đại diện"
                    className="im-sr-only"
                    type="file"
                    accept="image/png,image/jpeg,image/webp"
                    onChange={(e) => void choose(e.target.files?.[0])}
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
                  {avatar && (
                    <Button
                      variant="ghost"
                      type="button"
                      className="im-text-button im-danger"
                      onClick={() => setAvatar("")}
                    >
                      Xóa ảnh đang chọn
                    </Button>
                  )}
                  {error && (
                    <p className="im-form-error" role="alert">
                      {error}
                    </p>
                  )}
                </>
              )}
            </div>
          </Panel>
          <Panel title="Thông tin tài khoản">
            <dl className="im-details">
              <div>
                <dt>Tên đăng nhập</dt>
                <dd>{state.profile.username}</dd>
              </div>
              <div>
                <dt>Họ và tên</dt>
                <dd>{state.profile.name}</dd>
              </div>
              <div>
                <dt>Mã nhân viên</dt>
                <dd>KTV-001</dd>
              </div>
              <div>
                <dt>Vai trò</dt>
                <dd>Kỹ thuật viên chẩn đoán hình ảnh</dd>
              </div>
              <div>
                <dt>Liên hệ công việc</dt>
                <dd>Nội bộ 105</dd>
              </div>
            </dl>
            <div className="im-panel-body">
              <Info>
                Hồ sơ nhân viên, tên đăng nhập và vai trò do Administrator quản
                lý. Bạn có thể cập nhật ảnh đại diện và đổi mật khẩu của mình.
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
            <ActionLink secondary href="/imaging/profile">
              Hủy
            </ActionLink>
            <Button className="im-button" type="submit">
              Lưu thay đổi
            </Button>
          </div>
        )}
      </form>
    </>
  );
}
