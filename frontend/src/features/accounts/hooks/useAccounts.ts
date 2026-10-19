"use client";

import { useCallback, useEffect, useState } from "react";
import {
  createStaffAccount,
  listAccounts,
  lockAccount,
  resendStaffInvitation,
  unlockAccount,
  updateAccountRole,
} from "../api/accountApi";
import type {
  CreateStaffInput,
  StaffRole,
  UserAccount,
} from "../types/account";

export function useAccounts() {
  const [accounts, setAccounts] = useState<UserAccount[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await listAccounts();
      setAccounts(data);
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Không thể tải danh sách tài khoản.",
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- tải dữ liệu lần đầu
    void reload();
  }, [reload]);

  const addStaffAccount = async (input: CreateStaffInput) => {
    const created = await createStaffAccount(input);
    setAccounts((current) => [created, ...current]);
    return created;
  };

  const lockUserAccount = async (id: string, reason: string) => {
    const updated = await lockAccount(id, reason);
    setAccounts((current) =>
      current.map((item) => (item.id === id ? updated : item)),
    );
    return updated;
  };

  const unlockUserAccount = async (id: string) => {
    const updated = await unlockAccount(id);
    setAccounts((current) =>
      current.map((item) => (item.id === id ? updated : item)),
    );
    return updated;
  };

  const changeRole = async (id: string, role: StaffRole) => {
    const updated = await updateAccountRole(id, role);
    setAccounts((current) =>
      current.map((item) => (item.id === id ? updated : item)),
    );
    return updated;
  };

  const resendInvitation = async (id: string) => {
    await resendStaffInvitation(id);
  };

  return {
    accounts,
    loading,
    error,
    reload,
    addStaffAccount,
    lockUserAccount,
    unlockUserAccount,
    changeRole,
    resendInvitation,
  };
}
