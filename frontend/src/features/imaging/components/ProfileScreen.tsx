"use client";

import type { ReactNode } from "react";
import Image from "next/image";
import { Button } from "@/components/ui/button";
import { ActionLink } from "./ActionLink";
import { Badge } from "./Badge";
import { Icon } from "./Icon";
import { PageHeading } from "./PageHeading";
import { Panel } from "./Panel";

export type ImagingAccountView = {
  username: string;
  displayName: string;
  roleLabel: string;
  active: boolean;
  avatarUrl: string | null;
  canEditAvatar: boolean;
  canChangePassword: boolean;
};

/** Account data and mutations are composed by imaging-shell, outside preview state. */
export function ProfileScreen({
  edit = false,
  account,
  loading,
  error,
  avatarError,
  reload,
  avatarEditor,
}: {
  edit?: boolean;
  account: ImagingAccountView | null;
  loading: boolean;
  error: string | null;
  avatarError: string | null;
  reload: () => void;
  avatarEditor?: ReactNode;
}) {
  return (
    <>
      <PageHeading
        eyebrow="CÁ NHÂN"
        title={edit ? "Cập nhật tài khoản" : "Tài khoản của tôi"}
        description={
          edit
            ? "Thay đổi ảnh đại diện của tài khoản."
            : "Thông tin tài khoản của bạn."
        }
      >
        {!edit && account?.canEditAvatar && (
          <ActionLink secondary href="/imaging/profile/edit">
            <Icon name="user" />
            Cập nhật tài khoản
          </ActionLink>
        )}
      </PageHeading>
      {loading && <p role="status">Đang tải thông tin tài khoản...</p>}
      {error && (
        <div role="alert" className="im-form-error">
          <p>{error}</p>
          <Button variant="outline" onClick={reload}>
            Thử lại
          </Button>
        </div>
      )}
      {account && (
        <div className="im-account-layout">
          <Panel>
            <div className="im-profile-card">
              {edit ? (
                avatarEditor
              ) : (
                <div className="im-profile-avatar">
                  {account.avatarUrl ? (
                    <Image
                      src={account.avatarUrl}
                      alt="Ảnh đại diện tài khoản"
                      width={108}
                      height={108}
                      unoptimized
                    />
                  ) : (
                    <Icon name="user" />
                  )}
                </div>
              )}
              <h2>{account.displayName}</h2>
              <p>{account.roleLabel}</p>
              <Badge tone={account.active ? "green" : "neutral"}>
                {account.active
                  ? "Tài khoản đang hoạt động"
                  : "Tài khoản đã vô hiệu hóa"}
              </Badge>
              {avatarError && (
                <div role="alert" className="im-form-error">
                  <p>{avatarError}</p>
                  <Button variant="outline" onClick={reload}>
                    Tải lại ảnh
                  </Button>
                </div>
              )}
            </div>
          </Panel>
          <Panel title="Thông tin tài khoản">
            <dl className="im-details">
              <div>
                <dt>Tên đăng nhập</dt>
                <dd>{account.username}</dd>
              </div>
              <div>
                <dt>Tên hiển thị</dt>
                <dd>{account.displayName}</dd>
              </div>
              <div>
                <dt>Vai trò</dt>
                <dd>{account.roleLabel}</dd>
              </div>
            </dl>
            {!edit && account.canChangePassword && (
              <div className="im-panel-body">
                <ActionLink secondary href="/imaging/change-password">
                  <Icon name="lock" />
                  Đổi mật khẩu
                </ActionLink>
              </div>
            )}
          </Panel>
        </div>
      )}
      {edit && (
        <div className="im-action-footer">
          <ActionLink secondary href="/imaging/profile">
            Quay lại tài khoản
          </ActionLink>
        </div>
      )}
    </>
  );
}
