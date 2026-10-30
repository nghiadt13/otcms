"use client";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { ApiError } from "@/lib/api/apiError";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import {
  Table,
  TableHeader,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
} from "@/components/ui/table";
import { TablePagination } from "@/components/admin/TablePagination";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { ConfirmActionDialog } from "@/components/admin/ConfirmActionDialog";
import {
  administratorError,
  changeAdministratorStatus,
  listAdministrators,
  statusLabel,
  type Administrator,
  type AdministratorPage,
} from "../api/administratorApi";
import { AdministratorDetail } from "./AdministratorDetail";
import { AdministratorCredentialDialog } from "./AdministratorCredentialDialog";

export function AdministratorManagement() {
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<{
    key: string;
    data?: AdministratorPage;
    error?: string;
    invalidCriteria?: boolean;
  } | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [target, setTarget] = useState<Administrator | null>(null);
  const [credential, setCredential] = useState<Administrator | "create" | null>(
    null,
  );
  const key = JSON.stringify([query, status, page, attempt]);
  const ready = result?.key === key;
  const data = ready ? result.data : undefined;
  const error = ready ? result.error : undefined;
  const reload = () => setAttempt((value) => value + 1);
  useEffect(() => {
    const controller = new AbortController();
    const timer = setTimeout(() => {
      listAdministrators(query.trim(), status, page, controller.signal)
        .then((data) => {
          if (!controller.signal.aborted) setResult({ key, data });
        })
        .catch((error) => {
          if (!controller.signal.aborted)
            setResult({
              key,
              error: administratorError(error, "search"),
              invalidCriteria:
                error instanceof ApiError && error.status === 400,
            });
        });
    }, 250);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [query, status, page, key]);
  return (
    <>
      <PageHeader
        title="Tài khoản Admin"
        description="Tra cứu và quản lý quyền truy cập của quản trị viên phòng khám."
        actions={
          <Button onClick={() => setCredential("create")}>
            Tạo tài khoản Admin
          </Button>
        }
      />
      <div className="mb-4 flex flex-wrap gap-3">
        <Input
          className="w-full sm:max-w-sm"
          aria-label="Tìm tài khoản Admin"
          placeholder="Tìm theo tên đăng nhập…"
          aria-invalid={ready && !!result.invalidCriteria}
          aria-describedby={
            ready && result.invalidCriteria ? "owner-search-error" : undefined
          }
          value={query}
          onChange={(event) => {
            setQuery(event.target.value);
            setPage(0);
          }}
        />
        <NativeSelect
          aria-label="Trạng thái"
          value={status}
          onChange={(event) => {
            setStatus(event.target.value);
            setPage(0);
          }}
        >
          <NativeSelectOption value="">Tất cả trạng thái</NativeSelectOption>
          <NativeSelectOption value="ACTIVE">Đang hoạt động</NativeSelectOption>
          <NativeSelectOption value="DISABLED">
            Đã vô hiệu hóa
          </NativeSelectOption>
          <NativeSelectOption value="TEMPORARILY_LOCKED">
            Tạm khóa
          </NativeSelectOption>
          <NativeSelectOption value="PENDING_VERIFICATION">
            Chờ xác minh
          </NativeSelectOption>
          <NativeSelectOption value="ARCHIVED">Đã lưu trữ</NativeSelectOption>
        </NativeSelect>
        <Button variant="outline" onClick={reload}>
          Tải lại
        </Button>
      </div>
      {ready && result.invalidCriteria && (
        <p
          id="owner-search-error"
          role="alert"
          className="mb-4 text-sm text-destructive"
        >
          {error}
        </p>
      )}
      <div className="rounded-lg border bg-card">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Tên đăng nhập</TableHead>
              <TableHead>Loại tài khoản</TableHead>
              <TableHead>Trạng thái</TableHead>
              <TableHead>Ngày tạo</TableHead>
              <TableHead className="text-right">Thao tác</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {!ready ? (
              <TableRow>
                <TableCell colSpan={5}>
                  <p
                    role="status"
                    className="py-8 text-center text-muted-foreground"
                  >
                    Đang tải danh sách…
                  </p>
                </TableCell>
              </TableRow>
            ) : error ? (
              <TableRow>
                <TableCell colSpan={5}>
                  <div className="space-y-3 py-8 text-center">
                    <p
                      role={result?.invalidCriteria ? undefined : "alert"}
                      className="text-destructive"
                    >
                      {result?.invalidCriteria
                        ? "Hãy điều chỉnh tiêu chí tìm kiếm phía trên."
                        : error}
                    </p>
                    <Button variant="outline" onClick={reload}>
                      Thử lại
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ) : !data?.content.length ? (
              <TableRow>
                <TableCell
                  colSpan={5}
                  className="py-10 text-center text-muted-foreground"
                >
                  {query || status
                    ? "Không tìm thấy tài khoản phù hợp. Hãy thay đổi bộ lọc."
                    : "Chưa có tài khoản Admin."}
                </TableCell>
              </TableRow>
            ) : (
              data.content.map((account) => (
                <TableRow key={account.accountId}>
                  <TableCell className="font-medium">
                    {account.username}
                  </TableCell>
                  <TableCell>Administrator</TableCell>
                  <TableCell>
                    <StatusBadge
                      tone={account.status === "ACTIVE" ? "success" : "neutral"}
                    >
                      {statusLabel(account.status)}
                    </StatusBadge>
                  </TableCell>
                  <TableCell>
                    {new Date(account.createdAt).toLocaleDateString("vi-VN")}
                  </TableCell>
                  <TableCell>
                    <div className="flex justify-end gap-1">
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => setSelected(account.accountId)}
                      >
                        Xem
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => setTarget(account)}
                      >
                        {account.status === "ACTIVE"
                          ? "Vô hiệu hóa"
                          : "Kích hoạt"}
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => setCredential(account)}
                      >
                        Cấp lại mật khẩu
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>
      {data && (
        <TablePagination
          page={page}
          totalPages={data.totalPages}
          totalItems={data.totalElements}
          onPageChange={setPage}
        />
      )}
      {selected && (
        <AdministratorDetail
          key={selected}
          id={selected}
          onClose={() => setSelected(null)}
          onStatus={(account) => {
            setSelected(null);
            setTarget(account);
          }}
          onReissue={(account) => {
            setSelected(null);
            setCredential(account);
          }}
        />
      )}
      {credential && (
        <AdministratorCredentialDialog
          account={credential === "create" ? undefined : credential}
          onClose={() => setCredential(null)}
          onSaved={reload}
        />
      )}
      <ConfirmActionDialog
        open={!!target}
        onOpenChange={(open) => {
          if (!open) setTarget(null);
        }}
        title={
          target?.status === "ACTIVE"
            ? "Vô hiệu hóa tài khoản Admin?"
            : "Kích hoạt tài khoản Admin?"
        }
        description={
          target?.status === "ACTIVE"
            ? `${target.username} sẽ không thể đăng nhập, các phiên đăng nhập bị thu hồi. Dữ liệu liên quan được giữ nguyên.`
            : `Cho phép ${target?.username ?? ""} đăng nhập lại vào hệ thống.`
        }
        confirmLabel={target?.status === "ACTIVE" ? "Vô hiệu hóa" : "Kích hoạt"}
        pendingLabel="Đang cập nhật…"
        destructive={target?.status === "ACTIVE"}
        onConfirm={async () => {
          if (!target) return;
          try {
            const updated = await changeAdministratorStatus(
              target.accountId,
              target.status === "ACTIVE" ? "deactivate" : "activate",
            );
            toast.success(
              `Trạng thái tài khoản đã được cập nhật thành ${statusLabel(updated.status)}.`,
            );
            reload();
          } catch (error) {
            toast.error(administratorError(error));
            if (!(error instanceof ApiError) || error.status !== 409) {
              setTarget(null);
              reload();
            }
            throw error;
          }
        }}
      />
    </>
  );
}
