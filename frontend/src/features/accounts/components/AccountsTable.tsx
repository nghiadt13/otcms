"use client";

import {
  ArrowDown,
  ArrowUp,
  ArrowUpDown,
  ChevronLeft,
  ChevronRight,
  Info,
  KeyRound,
  Lock,
  Mail,
  MoreHorizontal,
  SearchX,
  UserCheck,
  UserCog,
} from "lucide-react";
import { type KeyboardEvent, useMemo, useState } from "react";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import {
  ROLE_LABELS,
  STATUS_LABELS,
  type UserAccount,
} from "../types/account";
import { formatRelativeTime } from "../utils/formatTime";

export type SortField = "fullName" | "role" | "status" | "lastSignInAt";
export type SortOrder = "asc" | "desc";

type AccountsTableProps = {
  accounts: UserAccount[];
  loading: boolean;
  selectedAccountId?: string | null;
  currentAdminEmail?: string;
  onSelectAccount: (account: UserAccount) => void;
  onRequestLock: (account: UserAccount) => void;
  onUnlock: (account: UserAccount) => void;
  onResendInvitation: (account: UserAccount) => void;
  onResetPassword?: (account: UserAccount) => void;
  onResetFilters?: () => void;
  isFiltered?: boolean;
};

const ITEMS_PER_PAGE = 7;

function getInitials(name: string): string {
  const parts = name
    .replace(/^(BS\.|ThS\.|TS\.)\s*/i, "")
    .trim()
    .split(/\s+/)
    .filter(Boolean);
  if (parts.length === 0) return "U";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (
    parts[parts.length - 2][0] + parts[parts.length - 1][0]
  ).toUpperCase();
}

/**
 * Task 4: Badge vai trò dùng bộ màu trung tính thống nhất.
 * Riêng Quản trị viên (Admin) cấp bậc cao nhất được viền và nhấn nổi bật hơn.
 */
function RoleBadge({ role }: { role: UserAccount["role"] }) {
  if (role === "admin") {
    return (
      <Badge
        variant="outline"
        className="border-foreground/30 bg-muted/80 font-semibold text-foreground"
      >
        {ROLE_LABELS[role]}
      </Badge>
    );
  }

  return (
    <Badge
      variant="outline"
      className="border-border bg-muted/50 font-normal text-foreground"
    >
      {ROLE_LABELS[role]}
    </Badge>
  );
}

/**
 * Task 4: Màu xanh Teal CHỈ dùng cho "Đang hoạt động" (bg-secondary text-primary).
 * Tạm khóa dùng destructive. Chờ kích hoạt dùng neutral/muted.
 */
function StatusBadgeWithTooltip({ account }: { account: UserAccount }) {
  const { status, lockReason, lockedBy } = account;

  if (status === "active") {
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full border border-primary/20 bg-secondary px-2.5 py-0.5 text-xs font-medium text-primary">
        <span className="size-1.5 rounded-full bg-primary" />
        {STATUS_LABELS[status]}
      </span>
    );
  }

  if (status === "inactive") {
    const tooltipText = lockReason
      ? `Lý do: ${lockReason}${lockedBy ? ` (Bởi ${lockedBy})` : ""}`
      : "Tài khoản bị tạm khóa bởi quản trị viên";

    return (
      <Tooltip>
        <TooltipTrigger
          render={
            <span className="inline-flex cursor-help items-center gap-1.5 rounded-full border border-destructive/20 bg-destructive/10 px-2.5 py-0.5 text-xs font-medium text-destructive">
              <span className="size-1.5 rounded-full bg-destructive" />
              {STATUS_LABELS[status]}
              <Info className="size-3 opacity-70" />
            </span>
          }
        />
        <TooltipContent className="max-w-xs">{tooltipText}</TooltipContent>
      </Tooltip>
    );
  }

  return (
    <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-muted px-2.5 py-0.5 text-xs font-medium text-muted-foreground">
      <span className="size-1.5 rounded-full bg-muted-foreground" />
      {STATUS_LABELS[status]}
    </span>
  );
}

/**
 * Mục menu bị vô hiệu hóa kèm Tooltip giải thích khi là tài khoản của chính mình.
 */
function DisabledItemWithTooltip({
  icon: Icon,
  label,
  variant = "default",
  tooltip = "Bạn không thể thao tác trên tài khoản của chính mình",
}: {
  icon: typeof Lock;
  label: string;
  variant?: "default" | "destructive";
  tooltip?: string;
}) {
  return (
    <Tooltip>
      <TooltipTrigger
        render={
          <div className="w-full">
            <DropdownMenuItem
              disabled
              variant={variant}
              className="h-9 w-full cursor-not-allowed gap-2 px-2.5 text-[13px] opacity-40 select-none"
            >
              <Icon className="size-4 shrink-0" />
              <span>{label}</span>
            </DropdownMenuItem>
          </div>
        }
      />
      <TooltipContent side="left" className="z-50 text-xs">
        {tooltip}
      </TooltipContent>
    </Tooltip>
  );
}

export function AccountsTable({
  accounts,
  loading,
  selectedAccountId,
  currentAdminEmail = "admin@otcms.local",
  onSelectAccount,
  onRequestLock,
  onUnlock,
  onResendInvitation,
  onResetPassword,
  onResetFilters,
  isFiltered,
}: AccountsTableProps) {
  const [sortField, setSortField] = useState<SortField>("fullName");
  const [sortOrder, setSortOrder] = useState<SortOrder>("asc");
  const [currentPage, setCurrentPage] = useState(1);

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      setSortOrder((prev) => (prev === "asc" ? "desc" : "asc"));
    } else {
      setSortField(field);
      setSortOrder("asc");
    }
  };

  const sortedAccounts = useMemo(() => {
    const sorted = [...accounts].sort((a, b) => {
      let aVal: string | number = a[sortField] ?? "";
      let bVal: string | number = b[sortField] ?? "";

      if (sortField === "lastSignInAt") {
        aVal = a.lastSignInAt ? new Date(a.lastSignInAt).getTime() : 0;
        bVal = b.lastSignInAt ? new Date(b.lastSignInAt).getTime() : 0;
      }

      if (aVal < bVal) return sortOrder === "asc" ? -1 : 1;
      if (aVal > bVal) return sortOrder === "asc" ? 1 : -1;
      return 0;
    });
    return sorted;
  }, [accounts, sortField, sortOrder]);

  const totalPages = Math.max(1, Math.ceil(sortedAccounts.length / ITEMS_PER_PAGE));
  const validCurrentPage = Math.min(currentPage, totalPages);

  const paginatedAccounts = useMemo(() => {
    const start = (validCurrentPage - 1) * ITEMS_PER_PAGE;
    return sortedAccounts.slice(start, start + ITEMS_PER_PAGE);
  }, [sortedAccounts, validCurrentPage]);

  const handleKeyDownRow = (
    event: KeyboardEvent<HTMLTableRowElement>,
    account: UserAccount,
  ) => {
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      onSelectAccount(account);
    }
  };

  const renderSortIcon = (field: SortField) => {
    if (sortField !== field) {
      return <ArrowUpDown className="size-3.5 opacity-40" />;
    }
    return sortOrder === "asc" ? (
      <ArrowUp className="size-3.5 text-primary" />
    ) : (
      <ArrowDown className="size-3.5 text-primary" />
    );
  };

  return (
    <TooltipProvider delay={150}>
      <div className="rounded-lg border border-border bg-card shadow-xs">
        <div className="overflow-x-auto">
          <Table>
            <TableHeader className="sticky top-0 z-10 border-b border-border bg-muted/40 backdrop-blur-xs">
              <TableRow className="hover:bg-transparent">
                <TableHead className="w-[32%] py-3">
                  <button
                    type="button"
                    onClick={() => handleSort("fullName")}
                    className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground hover:text-foreground"
                  >
                    Người dùng {renderSortIcon("fullName")}
                  </button>
                </TableHead>

                <TableHead className="hidden md:table-cell w-[18%] py-3">
                  <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                    Số điện thoại
                  </span>
                </TableHead>

                <TableHead className="w-[18%] py-3">
                  <button
                    type="button"
                    onClick={() => handleSort("role")}
                    className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground hover:text-foreground"
                  >
                    Vai trò {renderSortIcon("role")}
                  </button>
                </TableHead>

                <TableHead className="w-[16%] py-3">
                  <button
                    type="button"
                    onClick={() => handleSort("status")}
                    className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground hover:text-foreground"
                  >
                    Trạng thái {renderSortIcon("status")}
                  </button>
                </TableHead>

                <TableHead className="hidden lg:table-cell w-[16%] py-3">
                  <button
                    type="button"
                    onClick={() => handleSort("lastSignInAt")}
                    className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground hover:text-foreground"
                  >
                    Đăng nhập gần nhất {renderSortIcon("lastSignInAt")}
                  </button>
                </TableHead>

                <TableHead className="w-14 text-right py-3 pr-4">
                  <span className="sr-only">Thao tác</span>
                </TableHead>
              </TableRow>
            </TableHeader>

            <TableBody>
              {loading ? (
                Array.from({ length: 5 }, (_, index) => (
                  <TableRow key={index} className="hover:bg-transparent">
                    <TableCell className="py-3">
                      <div className="flex items-center gap-3">
                        <Skeleton className="size-8 rounded-full" />
                        <div className="space-y-1">
                          <Skeleton className="h-4 w-32" />
                          <Skeleton className="h-3 w-40" />
                        </div>
                      </div>
                    </TableCell>
                    <TableCell className="hidden md:table-cell py-3">
                      <Skeleton className="h-4 w-24" />
                    </TableCell>
                    <TableCell className="py-3">
                      <Skeleton className="h-5 w-24 rounded-full" />
                    </TableCell>
                    <TableCell className="py-3">
                      <Skeleton className="h-5 w-20 rounded-full" />
                    </TableCell>
                    <TableCell className="hidden lg:table-cell py-3">
                      <Skeleton className="h-4 w-24" />
                    </TableCell>
                    <TableCell className="text-right py-3 pr-4">
                      <Skeleton className="ml-auto size-8 rounded-md" />
                    </TableCell>
                  </TableRow>
                ))
              ) : paginatedAccounts.length === 0 ? (
                <TableRow className="hover:bg-transparent">
                  <TableCell
                    colSpan={6}
                    className="py-16 text-center text-muted-foreground"
                  >
                    <div className="flex flex-col items-center justify-center gap-2">
                      <SearchX className="size-8 text-muted-foreground/60" />
                      <p className="text-sm font-medium text-foreground">
                        Không tìm thấy người dùng phù hợp
                      </p>
                      <p className="text-xs text-muted-foreground">
                        {isFiltered
                          ? "Thử thay đổi từ khóa tìm kiếm hoặc bỏ bớt tiêu chí lọc."
                          : "Chưa có tài khoản nào trong hệ thống."}
                      </p>
                      {isFiltered && onResetFilters && (
                        <Button
                          variant="outline"
                          size="sm"
                          className="mt-2"
                          onClick={onResetFilters}
                        >
                          Xóa bộ lọc
                        </Button>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ) : (
                paginatedAccounts.map((account) => {
                  const isSelected = selectedAccountId === account.id;
                  const lastLogin = formatRelativeTime(account.lastSignInAt);
                  const isSelf =
                    account.email.toLowerCase() ===
                    currentAdminEmail.toLowerCase();

                  return (
                    <TableRow
                      key={account.id}
                      tabIndex={0}
                      onKeyDown={(event) => handleKeyDownRow(event, account)}
                      onClick={() => onSelectAccount(account)}
                      aria-label={`Xem chi tiết tài khoản ${account.fullName}`}
                      className={cn(
                        "group cursor-pointer transition-colors duration-100",
                        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset",
                        isSelected
                          ? "bg-secondary/40 hover:bg-secondary/50 font-medium"
                          : "hover:bg-muted/50",
                      )}
                    >
                      <TableCell className="py-3">
                        <div className="flex items-center gap-3">
                          <Avatar size="default">
                            <AvatarFallback className="bg-muted text-foreground font-semibold text-xs border border-border">
                              {getInitials(account.fullName)}
                            </AvatarFallback>
                          </Avatar>
                          <div className="min-w-0">
                            <div className="truncate text-sm font-medium text-foreground group-hover:text-primary transition-colors">
                              {account.fullName}
                            </div>
                            <div className="truncate text-xs text-muted-foreground">
                              {account.email}
                            </div>
                          </div>
                        </div>
                      </TableCell>

                      <TableCell className="hidden md:table-cell py-3 text-xs text-muted-foreground tabular-nums">
                        {account.phone || "—"}
                      </TableCell>

                      <TableCell className="py-3">
                        <RoleBadge role={account.role} />
                      </TableCell>

                      <TableCell className="py-3">
                        <StatusBadgeWithTooltip account={account} />
                      </TableCell>

                      <TableCell className="hidden lg:table-cell py-3 text-xs tabular-nums text-muted-foreground">
                        <Tooltip>
                          <TooltipTrigger
                            render={
                              <span className="cursor-help hover:text-foreground hover:underline decoration-dotted underline-offset-2">
                                {lastLogin.relative}
                              </span>
                            }
                          />
                          <TooltipContent>{lastLogin.full}</TooltipContent>
                        </Tooltip>
                      </TableCell>

                      <TableCell
                        className="text-right py-3 pr-4"
                        onClick={(e) => e.stopPropagation()}
                      >
                        <DropdownMenu>
                          <DropdownMenuTrigger
                            render={
                              <Button
                                variant="ghost"
                                size="sm"
                                className="size-8 rounded-lg p-0 hover:bg-muted text-muted-foreground hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1"
                                aria-label={`Thao tác cho ${account.fullName}`}
                              />
                            }
                          >
                            <MoreHorizontal className="size-4" />
                          </DropdownMenuTrigger>
                          <DropdownMenuContent
                            align="end"
                            side="bottom"
                            sideOffset={4}
                            className="w-auto min-w-[200px] whitespace-nowrap p-1 text-[13px] shadow-md border border-border"
                          >
                            {/* 1. Trạng thái Đang hoạt động */}
                            {account.status === "active" && (
                              <>
                                {isSelf ? (
                                  <DisabledItemWithTooltip
                                    icon={UserCog}
                                    label="Đổi vai trò"
                                  />
                                ) : (
                                  <DropdownMenuItem
                                    className="h-9 px-2.5 text-[13px] gap-2 cursor-pointer"
                                    onClick={() => onSelectAccount(account)}
                                  >
                                    <UserCog className="size-4 shrink-0 text-muted-foreground" />
                                    <span>Đổi vai trò</span>
                                  </DropdownMenuItem>
                                )}

                                <DropdownMenuItem
                                  className="h-9 px-2.5 text-[13px] gap-2 cursor-pointer"
                                  onClick={() => onResetPassword?.(account)}
                                >
                                  <KeyRound className="size-4 shrink-0 text-muted-foreground" />
                                  <span>Đặt lại mật khẩu</span>
                                </DropdownMenuItem>

                                <DropdownMenuSeparator className="my-1" />

                                {isSelf ? (
                                  <DisabledItemWithTooltip
                                    icon={Lock}
                                    label="Khóa tài khoản"
                                    variant="destructive"
                                  />
                                ) : (
                                  <DropdownMenuItem
                                    variant="destructive"
                                    className="h-9 px-2.5 text-[13px] gap-2 cursor-pointer text-destructive focus:bg-destructive/10 focus:text-destructive"
                                    onClick={() => onRequestLock(account)}
                                  >
                                    <Lock className="size-4 shrink-0" />
                                    <span>Khóa tài khoản</span>
                                  </DropdownMenuItem>
                                )}
                              </>
                            )}

                            {/* 2. Trạng thái Chờ kích hoạt */}
                            {account.status === "pending_invitation" && (
                              <>
                                <DropdownMenuItem
                                  className="h-9 px-2.5 text-[13px] gap-2 cursor-pointer"
                                  onClick={() => onResendInvitation(account)}
                                >
                                  <Mail className="size-4 shrink-0 text-muted-foreground" />
                                  <span>Gửi lại lời mời kích hoạt</span>
                                </DropdownMenuItem>

                                {isSelf ? (
                                  <DisabledItemWithTooltip
                                    icon={UserCog}
                                    label="Đổi vai trò"
                                  />
                                ) : (
                                  <DropdownMenuItem
                                    className="h-9 px-2.5 text-[13px] gap-2 cursor-pointer"
                                    onClick={() => onSelectAccount(account)}
                                  >
                                    <UserCog className="size-4 shrink-0 text-muted-foreground" />
                                    <span>Đổi vai trò</span>
                                  </DropdownMenuItem>
                                )}
                              </>
                            )}

                            {/* 3. Trạng thái Tạm khóa */}
                            {account.status === "inactive" && (
                              <DropdownMenuItem
                                className="h-9 px-2.5 text-[13px] gap-2 cursor-pointer text-primary focus:bg-primary/10 focus:text-primary"
                                onClick={() => onUnlock(account)}
                              >
                                <UserCheck className="size-4 shrink-0" />
                                <span>Mở khóa tài khoản</span>
                              </DropdownMenuItem>
                            )}
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </TableCell>
                    </TableRow>
                  );
                })
              )}
            </TableBody>
          </Table>
        </div>

        {/* Pagination và số đếm kết quả */}
        {!loading && sortedAccounts.length > 0 && (
          <div className="flex flex-col sm:flex-row items-center justify-between gap-3 border-t border-border px-4 py-3 text-xs text-muted-foreground">
            <div>
              Hiển thị{" "}
              <strong className="text-foreground tabular-nums">
                {(validCurrentPage - 1) * ITEMS_PER_PAGE + 1}–
                {Math.min(validCurrentPage * ITEMS_PER_PAGE, sortedAccounts.length)}
              </strong>{" "}
              trên tổng số{" "}
              <strong className="text-foreground tabular-nums">
                {sortedAccounts.length}
              </strong>{" "}
              người dùng
            </div>

            <div className="flex items-center gap-1.5">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                disabled={validCurrentPage <= 1}
                aria-label="Trang trước"
                className="h-8 px-2.5"
              >
                <ChevronLeft className="size-4" />
                <span className="hidden sm:inline">Trước</span>
              </Button>

              <span className="px-2 font-medium tabular-nums text-foreground">
                Trang {validCurrentPage} / {totalPages}
              </span>

              <Button
                variant="outline"
                size="sm"
                onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                disabled={validCurrentPage >= totalPages}
                aria-label="Trang sau"
                className="h-8 px-2.5"
              >
                <span className="hidden sm:inline">Sau</span>
                <ChevronRight className="size-4" />
              </Button>
            </div>
          </div>
        )}
      </div>
    </TooltipProvider>
  );
}
