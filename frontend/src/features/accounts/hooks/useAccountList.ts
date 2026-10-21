"use client";

import { useEffect, useState } from "react";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { usePagedList } from "@/hooks/usePagedList";
import { listAccounts } from "../api/accountApi";
import type { AccountListItem, AccountListQuery } from "../types/account";

type AccountFilters = Pick<AccountListQuery, "q" | "role" | "status">;

/**
 * Danh sách tài khoản: phân trang phía server. Ô tìm kiếm có `searchText` riêng để gõ
 * mượt; chỉ sau 250 ms mới đẩy vào bộ lọc (và về trang 0).
 */
export function useAccountList() {
  const list = usePagedList<AccountListItem, AccountFilters>(
    listAccounts,
    {},
  );
  const { setFilters } = list;
  const [searchText, setSearchText] = useState("");
  const debounced = useDebouncedValue(searchText.trim());
  const currentQ = list.filters.q ?? "";

  useEffect(() => {
    if (debounced !== currentQ) setFilters({ q: debounced || undefined });
  }, [debounced, currentQ, setFilters]);

  const hasFilter = Boolean(
    list.filters.q || list.filters.role || list.filters.status,
  );

  return { ...list, searchText, setSearchText, hasFilter };
}
