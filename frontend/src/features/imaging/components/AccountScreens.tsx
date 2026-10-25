"use client";

import Image from "next/image";
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
                  <input
                    ref={input}
                    aria-label="Chọn ảnh đại diện"
                    className="im-sr-only"
                    type="file"
                    accept="image/png,image/jpeg,image/webp"
                    onChange={(e) => void choose(e.target.files?.[0])}
                  />
                  <button
                    className="im-button im-button-secondary"
                    type="button"
                    onClick={() => input.current?.click()}
                  >
                    <Icon name="upload" />
                    Chọn ảnh đại diện
                  </button>
                  <small>JPG, PNG hoặc WebP · Tối đa 2 MB</small>
                  {avatar && (
                    <button
                      type="button"
                      className="im-text-button im-danger"
                      onClick={() => setAvatar("")}
                    >
                      Xóa ảnh đang chọn
                    </button>
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
            <button className="im-button" type="submit">
              Lưu thay đổi
            </button>
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
                  <input
                    name="current"
                    required
                    type={show ? "text" : "password"}
                    autoComplete="current-password"
                    placeholder="Nhập mật khẩu hiện tại"
                  />
                </label>
                <label className="im-field">
                  Mật khẩu mới
                  <input
                    name="next"
                    required
                    type={show ? "text" : "password"}
                    autoComplete="new-password"
                    placeholder="Tối thiểu 8 ký tự, có chữ và số"
                  />
                </label>
                <label className="im-field">
                  Xác nhận mật khẩu mới
                  <input
                    name="confirm"
                    required
                    type={show ? "text" : "password"}
                    autoComplete="new-password"
                    placeholder="Nhập lại mật khẩu mới"
                  />
                </label>
                <label className="im-check">
                  <input
                    type="checkbox"
                    checked={show}
                    onChange={(e) => setShow(e.target.checked)}
                  />
                  Hiển thị mật khẩu
                </label>
                {error && (
                  <p className="im-form-error" role="alert">
                    {error}
                  </p>
                )}
                <div className="im-row-actions">
                  <button className="im-button" type="submit">
                    <Icon name="lock" />
                    Cập nhật mật khẩu
                  </button>
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
    <div className="im-login">
      <aside className="im-login-aside">
        <Link href="/" className="im-brand">
          <span className="im-brand-mark">+</span>
          <strong>
            OTCMS<span>CARE MANAGEMENT</span>
          </strong>
        </Link>
        <div>
          <span className="im-login-illustration">
            <Icon name="layers" size={110} />
          </span>
          <p className="im-eyebrow">CHẨN ĐOÁN HÌNH ẢNH</p>
          <h1>
            Kết nối bộ ảnh.
            <br />
            Tiếp nối chăm sóc.
          </h1>
          <p>
            Một không gian cho danh sách chờ, chỉ định và các bộ ảnh của phòng.
          </p>
          <div className="im-login-feature">
            <Icon name="queue" />
            <span>Tiếp nhận đúng bệnh nhân</span>
          </div>
          <div className="im-login-feature">
            <Icon name="shield" />
            <span>Đối chiếu đúng bộ ảnh và chỉ định</span>
          </div>
          <div className="im-login-feature">
            <Icon name="upload" />
            <span>Gửi study đến bác sĩ yêu cầu</span>
          </div>
        </div>
        <small>OTCMS · Phòng khám chấn thương chỉnh hình</small>
      </aside>
      <main className="im-login-main">
        <span className="im-preview-pill">Bản xem thử giao diện</span>
        <div>
          <Badge tone="blue">DÀNH CHO NHÂN VIÊN</Badge>
          <h2>Chào mừng trở lại</h2>
          <p>Đăng nhập bằng tài khoản nhân viên được cấp.</p>
          <form onSubmit={submit}>
            <label className="im-field">
              Tên đăng nhập
              <input
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
                <input
                  name="password"
                  aria-label="Mật khẩu"
                  autoComplete="current-password"
                  required
                  type={show ? "text" : "password"}
                  placeholder="Nhập mật khẩu"
                />
                <button
                  type="button"
                  className="im-text-button"
                  onClick={() => setShow(!show)}
                >
                  {show ? "Ẩn" : "Hiện"}
                </button>
              </div>
            </label>
            {error && (
              <p className="im-form-error" role="alert">
                {error}
              </p>
            )}
            <button
              className="im-button im-login-submit"
              disabled={loading}
              type="submit"
            >
              {loading ? "Đang đăng nhập…" : "Đăng nhập"}
              <Icon name="arrow" size={17} />
            </button>
          </form>
          <div className="im-login-help">
            <Icon name="lock" size={18} />
            <p>
              Quên mật khẩu? Liên hệ Administrator để được cấp lại mật khẩu.
            </p>
          </div>
          <div className="im-demo-login">
            <strong>Tài khoản mẫu</strong>
            <p>ktv.minhanh / Demo1234</p>
            <small>
              Dùng ktv.moi / Demo1234 để xem luồng đổi mật khẩu lần đầu.
            </small>
          </div>
          <Link href="/imaging" className="im-text-button">
            Xem trực tiếp giao diện <Icon name="arrow" size={15} />
          </Link>
        </div>
      </main>
    </div>
  );
}
