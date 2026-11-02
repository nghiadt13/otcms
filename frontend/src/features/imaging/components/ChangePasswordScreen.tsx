"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { changeFirstLoginPassword, FirstLoginPasswordError } from "@/lib/auth/changePasswordApi";
import { ActionLink } from "./ActionLink";
import { Icon } from "./Icon";
import { Info } from "./Info";
import { PageHeading } from "./PageHeading";
import { Panel } from "./Panel";

export function ChangePasswordScreen() {
  const [error, setError] = useState("");
  const [success, setSuccess] = useState(false);
  const [show, setShow] = useState(false);
  const [pending, setPending] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const current = String(data.get("current") ?? "");
    const next = String(data.get("next") ?? "");
    const confirm = String(data.get("confirm") ?? "");
    setError("");
    if (next.length < 12 || next.length > 64 || new TextEncoder().encode(next).length > 72)
      return setError("Mật khẩu mới cần 12–64 ký tự và tối đa 72 byte UTF-8.");
    if (next === current) return setError("Mật khẩu mới phải khác mật khẩu hiện tại.");
    if (next !== confirm) return setError("Mật khẩu xác nhận chưa khớp.");
    setPending(true);
    try {
      await changeFirstLoginPassword(current, next);
      form.reset();
      setSuccess(true);
    } catch (cause) {
      const reason = cause instanceof FirstLoginPasswordError ? cause.reason : "service";
      setError({ current: "Mật khẩu hiện tại không đúng.", reused: "Mật khẩu mới phải khác mật khẩu hiện tại.", invalid: "Mật khẩu mới không hợp lệ.", session: "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.", service: "Không thể đổi mật khẩu. Vui lòng thử lại." }[reason]);
    } finally {
      setPending(false);
    }
  }

  return (
    <>
      <PageHeading eyebrow="BẢO MẬT TÀI KHOẢN" title="Đổi mật khẩu" description="Cập nhật mật khẩu để bảo vệ tài khoản của bạn." />
      <div className="im-password-layout">
        <Panel title="Thiết lập mật khẩu mới">
          <form className="im-panel-body im-password-form" onSubmit={submit}>
            {success ? (
              <>
                <div className="im-result-banner im-result-success" role="status"><Icon name="check" size={30} /><div><h2>Đổi mật khẩu thành công</h2><p>Vui lòng đăng nhập lại bằng mật khẩu mới.</p></div></div>
                <ActionLink href="/imaging/login">Đăng nhập lại <Icon name="arrow" size={16} /></ActionLink>
              </>
            ) : (
              <>
                <label className="im-field">Mật khẩu hiện tại<Input name="current" required disabled={pending} type={show ? "text" : "password"} autoComplete="current-password" placeholder="Nhập mật khẩu hiện tại" /></label>
                <label className="im-field">Mật khẩu mới<Input name="next" required disabled={pending} type={show ? "text" : "password"} autoComplete="new-password" placeholder="Từ 12 đến 64 ký tự" /></label>
                <label className="im-field">Xác nhận mật khẩu mới<Input name="confirm" required disabled={pending} type={show ? "text" : "password"} autoComplete="new-password" placeholder="Nhập lại mật khẩu mới" /></label>
                <label className="im-check"><input type="checkbox" checked={show} onChange={(e) => setShow(e.target.checked)} />Hiển thị mật khẩu</label>
                {error && <p className="im-form-error" role="alert">{error}</p>}
                <div className="im-row-actions"><Button className="im-button" type="submit" disabled={pending}><Icon name="lock" />{pending ? "Đang cập nhật..." : "Cập nhật mật khẩu"}</Button><ActionLink secondary href="/imaging/profile">Quay lại</ActionLink></div>
              </>
            )}
          </form>
        </Panel>
        <div className="im-stack"><Info>Mật khẩu mới phải dài 12–64 ký tự và không vượt quá 72 byte UTF-8. Sau khi đổi, hệ thống yêu cầu đăng nhập lại.</Info></div>
      </div>
    </>
  );
}
