"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Checkbox } from "@/components/ui/checkbox";

import Image from "next/image";
import { Card } from "@/components/ui/card";
import { OtcmsLogo } from "@/components/brand/OtcmsLogo";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef, useState, type FormEvent } from "react";
import { useImaging } from "../ImagingProvider";
import { ActionLink, Badge, Icon, Info, PageHeading, Panel } from "./ImagingUi";

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

export function ChangePasswordScreen() {
  const { notify, setFirstLogin } = useImaging();
  const [error, setError] = useState("");
  const [success, setSuccess] = useState(false);
  const [show, setShow] = useState(false);
  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    const current = String(data.get("current"));
    const next = String(data.get("next"));
    const confirm = String(data.get("confirm"));
    setError("");
    if (current !== "Demo1234")
      return setError(
        "Mật khẩu hiện tại chưa đúng. Trong bản xem thử, dùng Demo1234.",
      );
    if (next.length < 8 || !/[A-Za-z]/.test(next) || !/[0-9]/.test(next))
      return setError("Mật khẩu mới cần tối thiểu 8 ký tự, có cả chữ và số.");
    if (next === current)
      return setError("Mật khẩu mới cần khác mật khẩu hiện tại.");
    if (next !== confirm) return setError("Mật khẩu xác nhận chưa khớp.");
    e.currentTarget.reset();
    setSuccess(true);
    notify(
      "Đã hoàn tất thao tác đổi mật khẩu minh họa. Mật khẩu thật không bị thay đổi.",
    );
    setFirstLogin(false);
  }
  return (
    <>
      <PageHeading
        eyebrow="BẢO MẬT TÀI KHOẢN"
        title="Đổi mật khẩu"
        description="Cập nhật mật khẩu để bảo vệ tài khoản của bạn."
      />
      <div className="im-password-layout">
        <Panel title="Thiết lập mật khẩu mới">
          <form className="im-panel-body im-password-form" onSubmit={submit}>
            {success ? (
              <>
                <div className="im-result-banner im-result-success">
                  <Icon name="check" size={30} />
                  <div>
                    <h2>Hoàn tất thao tác xem thử</h2>
                    <p>
                      Không lưu hoặc thay đổi mật khẩu thật trong bản frontend.
                    </p>
                  </div>
                </div>
                <ActionLink href="/imaging">
                  Vào không gian làm việc <Icon name="arrow" size={16} />
                </ActionLink>
              </>
            ) : (
              <>
                <label className="im-field">
                  Mật khẩu hiện tại
                  <Input
                    name="current"
                    required
                    type={show ? "text" : "password"}
                    autoComplete="current-password"
                    placeholder="Nhập mật khẩu hiện tại"
                  />
                </label>
                <label className="im-field">
                  Mật khẩu mới
                  <Input
                    name="next"
                    required
                    type={show ? "text" : "password"}
                    autoComplete="new-password"
                    placeholder="Tối thiểu 8 ký tự, có chữ và số"
                  />
                </label>
                <label className="im-field">
                  Xác nhận mật khẩu mới
                  <Input
                    name="confirm"
                    required
                    type={show ? "text" : "password"}
                    autoComplete="new-password"
                    placeholder="Nhập lại mật khẩu mới"
                  />
                </label>
                <label className="im-check">
                  <Checkbox
                    checked={show}
                    onCheckedChange={(checked) => setShow(checked === true)}
                  />
                  Hiển thị mật khẩu
                </label>
                {error && (
                  <p className="im-form-error" role="alert">
                    {error}
                  </p>
                )}
                <div className="im-row-actions">
                  <Button className="im-button" type="submit">
                    <Icon name="lock" />
                    Cập nhật mật khẩu
                  </Button>
                  <ActionLink secondary href="/imaging/profile">
                    Quay lại
                  </ActionLink>
                </div>
              </>
            )}
          </form>
        </Panel>
        <div className="im-stack">
          <Info>
            Mật khẩu cần có tối thiểu 8 ký tự và bao gồm cả chữ và số. Khi đăng
            nhập bằng mật khẩu được cấp, cần đổi mật khẩu trước khi tiếp tục
            công việc.
          </Info>
          <p className="im-footnote">
            Mật khẩu hiện tại dùng để thử giao diện: <strong>Demo1234</strong>.
          </p>
        </div>
      </div>
    </>
  );
}

export function StaffLoginScreen() {
  const { setFirstLogin } = useImaging();
  const router = useRouter();
  const [error, setError] = useState("");
  const [show, setShow] = useState(false);
  const [loading, setLoading] = useState(false);
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    const user = String(data.get("username")).trim();
    const password = String(data.get("password"));
    if (!["ktv.minhanh", "ktv.moi"].includes(user) || password !== "Demo1234")
      return setError(
        "Tên đăng nhập hoặc mật khẩu chưa đúng. Sử dụng tài khoản mẫu bên dưới.",
      );
    setError("");
    setLoading(true);
    await new Promise((r) => setTimeout(r, 350));
    if (user === "ktv.moi") {
      setFirstLogin(true);
      router.push("/imaging/change-password");
    } else {
      setFirstLogin(false);
      router.push("/imaging");
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
            Tên đăng nhập
            <Input
              className="h-12 text-base"
              name="username"
              autoComplete="username"
              defaultValue="ktv.minhanh"
              required
              placeholder="Nhập tên đăng nhập"
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
          Quên mật khẩu? Liên hệ quản trị viên để được cấp lại.
        </p>
        <details className="mt-5 border-t pt-4 text-sm text-muted-foreground">
          <summary className="cursor-pointer">Tài khoản mẫu</summary>
          <p className="mt-2">ktv.minhanh / Demo1234</p>
          <p className="mt-1">ktv.moi / Demo1234: đổi mật khẩu lần đầu.</p>
        </details>
      </Card>
      <Link
        href="/imaging"
        className="text-sm font-medium text-primary hover:underline"
      >
        Xem trực tiếp giao diện
      </Link>
    </main>
  );
}
