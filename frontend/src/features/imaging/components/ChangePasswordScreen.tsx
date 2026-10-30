"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Checkbox } from "@/components/ui/checkbox";
import { useState, type FormEvent } from "react";
import { useImaging } from "../hooks/useImaging";
import { ActionLink } from "./ActionLink";
import { Icon } from "./Icon";
import { Info } from "./Info";
import { PageHeading } from "./PageHeading";
import { Panel } from "./Panel";

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
