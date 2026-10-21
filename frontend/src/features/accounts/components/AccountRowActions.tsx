"use client";

import { Power, PowerOff } from "lucide-react";
import {
  RowActionsMenu,
  type RowAction,
} from "@/components/admin/RowActionsMenu";
import { Button } from "@/components/ui/button";
import { accountIdentifier } from "../accountDisplay";
import type { StatusTarget } from "../hooks/useAccountStatusChange";
import type { AccountListItem } from "../types/account";

type AccountRowActionsProps = {
  account: AccountListItem;
  onView: (id: string) => void;
  onStatusRequest: (target: StatusTarget) => void;
};

/** Nút Xem + menu ⋯. Chỉ vẽ mục có trong `allowedActions`, không suy từ trạng thái. */
export function AccountRowActions({
  account,
  onView,
  onStatusRequest,
}: AccountRowActionsProps) {
  const identifier = accountIdentifier(account);
  const label = account.fullName ?? identifier;
  const actions: RowAction[] = [];
  if (account.allowedActions.includes("activate"))
    actions.push({
      key: "activate",
      label: "Kích hoạt",
      icon: Power,
      onSelect: () =>
        onStatusRequest({ id: account.id, label, action: "activate" }),
    });
  if (account.allowedActions.includes("deactivate"))
    actions.push({
      key: "deactivate",
      label: "Vô hiệu hóa",
      icon: PowerOff,
      destructive: true,
      onSelect: () =>
        onStatusRequest({ id: account.id, label, action: "deactivate" }),
    });

  return (
    <div className="flex items-center justify-end gap-1">
      <Button
        variant="ghost"
        size="sm"
        aria-label={`Xem tài khoản ${identifier}`}
        onClick={() => onView(account.id)}
      >
        Xem
      </Button>
      <RowActionsMenu actions={actions} label={identifier} />
    </div>
  );
}
