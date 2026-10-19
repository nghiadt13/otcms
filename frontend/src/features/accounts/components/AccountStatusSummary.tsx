"use client";

import { type KeyboardEvent, useRef } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import type { AccountStatus, UserAccount } from "../types/account";

export type SummaryFilter = AccountStatus | "all";

type AccountStatusSummaryProps = {
  accounts: UserAccount[];
  selectedFilter: SummaryFilter;
  onSelectFilter: (filter: SummaryFilter) => void;
  loading?: boolean;
};

export function AccountStatusSummary({
  accounts,
  selectedFilter,
  onSelectFilter,
  loading = false,
}: AccountStatusSummaryProps) {
  const tabRefs = useRef<(HTMLButtonElement | null)[]>([]);

  const totalCount = accounts.length;
  const activeCount = accounts.filter((a) => a.status === "active").length;
  const pendingCount = accounts.filter(
    (a) => a.status === "pending_invitation",
  ).length;
  const inactiveCount = accounts.filter((a) => a.status === "inactive").length;

  const tabs: {
    key: SummaryFilter;
    label: string;
    count: number;
    dotClass?: string;
  }[] = [
    {
      key: "all",
      label: "Tất cả",
      count: totalCount,
    },
    {
      key: "active",
      label: "Đang hoạt động",
      count: activeCount,
      dotClass: "bg-primary",
    },
    {
      key: "pending_invitation",
      label: "Chờ kích hoạt",
      count: pendingCount,
      dotClass: "bg-muted-foreground",
    },
    {
      key: "inactive",
      label: "Tạm khóa",
      count: inactiveCount,
      dotClass: "bg-destructive",
    },
  ];

  const handleKeyDown = (e: KeyboardEvent<HTMLButtonElement>, index: number) => {
    let nextIndex = index;
    if (e.key === "ArrowRight") {
      e.preventDefault();
      nextIndex = (index + 1) % tabs.length;
    } else if (e.key === "ArrowLeft") {
      e.preventDefault();
      nextIndex = (index - 1 + tabs.length) % tabs.length;
    }

    if (nextIndex !== index) {
      onSelectFilter(tabs[nextIndex].key);
      tabRefs.current[nextIndex]?.focus();
    }
  };

  return (
    <div
      role="tablist"
      aria-label="Lọc trạng thái tài khoản"
      className="flex items-center gap-4 sm:gap-6 border-b border-border mb-5 overflow-x-auto overflow-y-hidden text-sm scrollbar-none"
    >
      {tabs.map((tab, idx) => {
        const isSelected = selectedFilter === tab.key;

        return (
          <button
            key={tab.key}
            ref={(el) => {
              tabRefs.current[idx] = el;
            }}
            type="button"
            role="tab"
            aria-selected={isSelected}
            tabIndex={isSelected ? 0 : -1}
            onClick={() => onSelectFilter(tab.key)}
            onKeyDown={(e) => handleKeyDown(e, idx)}
            className={cn(
              "group relative flex items-center gap-1.5 pb-2.5 pt-1 text-sm transition-colors whitespace-nowrap",
              "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:rounded-t-sm",
              isSelected
                ? "border-b-2 border-primary text-foreground font-medium -mb-px"
                : "border-b-2 border-transparent text-muted-foreground hover:text-foreground hover:border-border",
            )}
          >
            {tab.dotClass && (
              <span
                className={cn("size-2 rounded-full shrink-0", tab.dotClass)}
                aria-hidden="true"
              />
            )}
            <span>{tab.label}</span>
            {loading ? (
              <Skeleton className="inline-block h-3.5 w-3.5 ml-1 rounded-xs" />
            ) : (
              <span className="text-xs text-muted-foreground font-normal tabular-nums ml-0.5">
                {tab.count}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}
