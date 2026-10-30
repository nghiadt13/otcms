"use client";

import { useState } from "react";
import { toast } from "sonner";
import { Plus } from "lucide-react";
import { PageHeader } from "@/components/PageHeader";
import { AdminFormField as Field } from "@/components/admin/AdminFormField";
import { ConfirmActionDialog } from "@/components/admin/ConfirmActionDialog";
import { RemoteDataState } from "@/components/admin/RemoteDataState";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { TablePagination } from "@/components/admin/TablePagination";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import { Textarea } from "@/components/ui/textarea";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useAdminRemoteData } from "@/hooks/useAdminRemoteData";
import { adminApiMessage } from "@/lib/api/adminApiMessage";
import {
  listAdminServices,
  saveAdminService,
  setAdminServiceActive,
  type AdminService,
} from "../api/adminServiceApi";
import { ServiceDoctorAssignments } from "./ServiceDoctorAssignments";

const loadServices = (signal: AbortSignal) => listAdminServices(signal);
const currency = new Intl.NumberFormat("vi-VN", {
  style: "currency",
  currency: "VND",
});

export function AdminServicesLive() {
  const remote = useAdminRemoteData(loadServices);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("all");
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<AdminService | "new" | null>(null);
  const [confirm, setConfirm] = useState<AdminService | null>(null);
  const [assigning, setAssigning] = useState<AdminService | null>(null);
  const [actionError, setActionError] = useState("");
  const items = (remote.data ?? []).filter(
    (s) =>
      s.name
        .toLocaleLowerCase("vi")
        .includes(search.trim().toLocaleLowerCase("vi")) &&
      (status === "all" || s.active === (status === "active")),
  );
  const totalPages = Math.max(1, Math.ceil(items.length / 10));
  const currentPage = Math.min(page, totalPages - 1);

  return (
    <>
      <PageHeader
        title="Dịch vụ và giá"
        description="Quản lý giá, thời lượng và khả năng đặt lịch online của dịch vụ."
        actions={
          <Button onClick={() => setEditing("new")}>
            <Plus />
            Thêm dịch vụ
          </Button>
        }
      />
      <div className="mb-4 grid gap-4 sm:grid-cols-[1fr_14rem]">
        <Field id="service-search" label="Tên dịch vụ">
          <Input
            id="service-search"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
            placeholder="Tìm theo tên dịch vụ"
          />
        </Field>
        <Field id="service-status" label="Trạng thái">
          <NativeSelect
            id="service-status"
            className="w-full"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
          >
            <option value="all">Tất cả</option>
            <option value="active">Đang cung cấp</option>
            <option value="inactive">Đã ngừng</option>
          </NativeSelect>
        </Field>
      </div>
      <RemoteDataState
        loading={remote.loading}
        error={remote.error}
        retry={remote.refresh}
      />
      {remote.data && (
        <>
          <div className="rounded-lg border bg-card">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Dịch vụ</TableHead>
                  <TableHead className="text-right">Giá</TableHead>
                  <TableHead>Thời lượng / đệm</TableHead>
                  <TableHead>Đặt online</TableHead>
                  <TableHead>Trạng thái</TableHead>
                  <TableHead className="text-right">Thao tác</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {items
                  .slice(currentPage * 10, currentPage * 10 + 10)
                  .map((service) => (
                    <TableRow key={service.id}>
                      <TableCell className="min-w-48 font-medium">
                        {service.name}
                      </TableCell>
                      <TableCell className="text-right tabular-nums">
                        {currency.format(service.price)}
                      </TableCell>
                      <TableCell>
                        {service.durationMinutes} / {service.bufferMinutes} phút
                      </TableCell>
                      <TableCell>
                        {service.onlineBookable ? "Có" : "Không"}
                      </TableCell>
                      <TableCell>
                        <StatusBadge
                          tone={service.active ? "success" : "neutral"}
                        >
                          {service.active ? "Đang cung cấp" : "Đã ngừng"}
                        </StatusBadge>
                      </TableCell>
                      <TableCell>
                        <div className="flex justify-end gap-2">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setAssigning(service)}
                          >
                            Bác sĩ
                          </Button>
                          {service.active && (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => setEditing(service)}
                            >
                              Sửa
                            </Button>
                          )}
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => {
                              setActionError("");
                              setConfirm(service);
                            }}
                          >
                            {service.active
                              ? "Ngừng cung cấp"
                              : "Kích hoạt lại"}
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                {!items.length && (
                  <TableRow>
                    <TableCell
                      colSpan={6}
                      className="h-24 text-center text-muted-foreground"
                    >
                      Không có dịch vụ phù hợp. Hãy đổi bộ lọc hoặc thêm dịch
                      vụ.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </div>
          <TablePagination
            page={currentPage}
            totalPages={totalPages}
            totalItems={items.length}
            onPageChange={setPage}
          />
        </>
      )}
      {editing && (
        <ServiceEditor
          service={editing === "new" ? undefined : editing}
          close={() => setEditing(null)}
          saved={() => {
            setEditing(null);
            remote.refresh();
          }}
        />
      )}
      {assigning && (
        <ServiceDoctorAssignments
          service={assigning}
          close={() => setAssigning(null)}
        />
      )}
      <ConfirmActionDialog
        open={!!confirm}
        onOpenChange={(open) => {
          if (!open) setConfirm(null);
        }}
        title={
          confirm?.active ? "Ngừng cung cấp dịch vụ?" : "Kích hoạt lại dịch vụ?"
        }
        description={
          <>
            {confirm?.name}.{" "}
            {confirm?.active
              ? "Dịch vụ sẽ không còn được chọn cho lịch mới. Dữ liệu lịch cũ được giữ lại."
              : "Dịch vụ sẽ trở lại danh mục đang cung cấp."}
            {actionError && (
              <span role="alert" className="mt-2 block text-destructive">
                {actionError}
              </span>
            )}
          </>
        }
        confirmLabel={confirm?.active ? "Ngừng cung cấp" : "Kích hoạt lại"}
        pendingLabel="Đang cập nhật..."
        destructive={confirm?.active}
        onConfirm={async () => {
          if (!confirm) return;
          try {
            await setAdminServiceActive(confirm.id, !confirm.active);
            toast.success("Đã cập nhật trạng thái dịch vụ.");
            remote.refresh();
          } catch (error) {
            setActionError(adminApiMessage(error));
            throw error;
          }
        }}
      />
    </>
  );
}

function ServiceEditor({
  service,
  close,
  saved,
}: {
  service?: AdminService;
  close: () => void;
  saved: () => void;
}) {
  const [name, setName] = useState(service?.name ?? "");
  const [description, setDescription] = useState(service?.description ?? "");
  const [price, setPrice] = useState(String(service?.price ?? ""));
  const [duration, setDuration] = useState(
    String(service?.durationMinutes ?? 20),
  );
  const [buffer, setBuffer] = useState(String(service?.bufferMinutes ?? 0));
  const [online, setOnline] = useState(service?.onlineBookable ?? false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !pending) close();
      }}
    >
      <DialogContent
        className="max-h-[90dvh] overflow-y-auto sm:max-w-xl"
        showCloseButton={!pending}
      >
        <DialogHeader>
          <DialogTitle>{service ? "Sửa dịch vụ" : "Thêm dịch vụ"}</DialogTitle>
          <DialogDescription>
            Giá và thời lượng mới áp dụng cho lịch tạo sau; thông tin lịch cũ
            được giữ nguyên.
          </DialogDescription>
        </DialogHeader>
        <form
          className="space-y-4"
          onSubmit={async (event) => {
            event.preventDefault();
            if (pending) return;
            const normalized = name.trim().replace(/\s+/g, " ");
            if (
              normalized.length < 2 ||
              !price.trim() ||
              !duration.trim() ||
              !buffer.trim() ||
              !Number.isFinite(Number(price)) ||
              Number(price) < 0 ||
              Number(price) > 9999999999.99 ||
              !/^\d+(\.\d{1,2})?$/.test(price) ||
              !Number.isInteger(Number(duration)) ||
              Number(duration) < 1 ||
              !Number.isInteger(Number(buffer)) ||
              Number(buffer) < 0
            ) {
              setError(
                "Nhập tên từ 2 ký tự, giá không âm và tối đa 2 số thập phân; thời lượng là số phút nguyên dương, thời gian đệm không âm.",
              );
              return;
            }
            setPending(true);
            setError("");
            try {
              await saveAdminService(service?.id, {
                name: normalized,
                description: description.trim() || null,
                price: Number(price),
                durationMinutes: Number(duration),
                bufferMinutes: Number(buffer),
                onlineBookable: online,
              });
              toast.success("Đã lưu dịch vụ.");
              saved();
            } catch (failure) {
              setError(adminApiMessage(failure));
            } finally {
              setPending(false);
            }
          }}
        >
          <fieldset disabled={pending} className="space-y-4">
            <Field id="live-service-name" label="Tên dịch vụ *">
              <Input
                id="live-service-name"
                required
                minLength={2}
                maxLength={160}
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </Field>
            <Field id="live-service-description" label="Mô tả">
              <Textarea
                id="live-service-description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </Field>
            <Field id="live-service-price" label="Giá (VND) *">
              <Input
                id="live-service-price"
                inputMode="decimal"
                required
                value={price}
                onChange={(e) => setPrice(e.target.value)}
              />
            </Field>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field id="live-service-duration" label="Thời lượng (phút) *">
                <Input
                  id="live-service-duration"
                  type="number"
                  min="1"
                  max="2147483647"
                  step="1"
                  required
                  value={duration}
                  onChange={(e) => setDuration(e.target.value)}
                />
              </Field>
              <Field
                id="live-service-buffer"
                label="Thời gian đệm (phút) *"
                hint="Khoảng trống sau mỗi lịch để chuẩn bị lượt tiếp theo."
              >
                <Input
                  id="live-service-buffer"
                  type="number"
                  min="0"
                  max="2147483647"
                  step="1"
                  required
                  value={buffer}
                  onChange={(e) => setBuffer(e.target.value)}
                />
              </Field>
            </div>
            <Field id="live-service-online" label="Cho phép đặt lịch online">
              <NativeSelect
                id="live-service-online"
                value={String(online)}
                onChange={(e) => setOnline(e.target.value === "true")}
              >
                <option value="false">Không</option>
                <option value="true">Có</option>
              </NativeSelect>
            </Field>
          </fieldset>
          {error && (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          )}
          <div className="flex justify-end gap-2">
            <Button
              type="button"
              variant="outline"
              disabled={pending}
              onClick={close}
            >
              Hủy
            </Button>
            <Button type="submit" disabled={pending}>
              {pending ? "Đang lưu..." : "Lưu dịch vụ"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
