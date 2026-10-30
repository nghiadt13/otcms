"use client";

import { useState } from "react";
import Link from "next/link";
import { Plus } from "lucide-react";
import { toast } from "sonner";
import { PageHeader } from "@/components/PageHeader";
import { AdminFormField as Field } from "@/components/admin/AdminFormField";
import { RemoteDataState } from "@/components/admin/RemoteDataState";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { TablePagination } from "@/components/admin/TablePagination";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { NativeSelect } from "@/components/ui/native-select";
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
import type { AdminDoctor } from "@/lib/domain/adminDoctor";
import {
  listAdminDoctors,
  listDoctorAccounts,
  saveDoctorDirectory,
  saveDoctorProfile,
  type DoctorProfileInput,
} from "../api/adminDoctorApi";

const loadDoctors = (signal: AbortSignal) => listAdminDoctors(signal);
const loadAccounts = (signal: AbortSignal) => listDoctorAccounts(signal);
const empty: DoctorProfileInput = {
  fullName: "",
  photoUrl: "",
  workContact: "",
  specialties: "",
  qualifications: "",
  biography: "",
  consultationInformation: "",
};

export function AdminDoctorsLive() {
  const remote = useAdminRemoteData(loadDoctors);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("all");
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<AdminDoctor | "new" | null>(null);
  const items = (remote.data ?? []).filter(
    (d) =>
      `${d.fullName} ${d.specialties} ${d.workContact ?? ""}`
        .toLocaleLowerCase("vi")
        .includes(search.trim().toLocaleLowerCase("vi")) &&
      (status === "all" || d.inActiveDirectory === (status === "active")),
  );
  const totalPages = Math.max(1, Math.ceil(items.length / 10));
  const currentPage = Math.min(page, totalPages - 1);
  return (
    <>
      <PageHeader
        title="Hồ sơ bác sĩ"
        description="Thông tin chuyên môn, hiển thị công khai và trạng thái nhận lịch của bác sĩ."
        actions={
          <Button onClick={() => setEditing("new")}>
            <Plus />
            Tạo hồ sơ bác sĩ
          </Button>
        }
      />
      <div className="mb-4 grid gap-4 sm:grid-cols-[1fr_14rem]">
        <Field id="doctor-search" label="Tìm bác sĩ">
          <Input
            id="doctor-search"
            placeholder="Tên, chuyên khoa hoặc liên hệ"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
          />
        </Field>
        <Field id="doctor-status" label="Danh mục">
          <NativeSelect
            id="doctor-status"
            className="w-full"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
          >
            <option value="all">Tất cả</option>
            <option value="active">Đang hoạt động</option>
            <option value="inactive">Đã gỡ khỏi danh mục</option>
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
                  <TableHead>Bác sĩ / chuyên khoa</TableHead>
                  <TableHead>Liên hệ công việc</TableHead>
                  <TableHead>Công khai</TableHead>
                  <TableHead>Nhận lịch mới</TableHead>
                  <TableHead>Thao tác</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {items
                  .slice(currentPage * 10, currentPage * 10 + 10)
                  .map((d) => (
                    <TableRow key={d.doctorProfileId}>
                      <TableCell className="min-w-48">
                        <p className="font-medium">{d.fullName}</p>
                        <p className="text-xs text-muted-foreground">
                          {d.specialties || "Chưa cập nhật chuyên khoa"}
                        </p>
                        {!d.inActiveDirectory && (
                          <StatusBadge tone="neutral">
                            Đã gỡ khỏi danh mục
                          </StatusBadge>
                        )}
                      </TableCell>
                      <TableCell>{d.workContact || "—"}</TableCell>
                      <TableCell>
                        {d.publiclyVisible && d.inActiveDirectory
                          ? "Có"
                          : "Không"}
                      </TableCell>
                      <TableCell>
                        <StatusBadge
                          tone={
                            d.acceptingNewBookings && d.inActiveDirectory
                              ? "success"
                              : "neutral"
                          }
                        >
                          {d.acceptingNewBookings && d.inActiveDirectory
                            ? "Đang nhận"
                            : "Tạm dừng"}
                        </StatusBadge>
                      </TableCell>
                      <TableCell>
                        <div className="flex flex-wrap gap-2">
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => setEditing(d)}
                          >
                            Xem / sửa
                          </Button>
                          <Link
                            className="inline-flex items-center text-sm font-medium text-primary underline-offset-4 hover:underline"
                            href={`/admin/staff-shifts?doctorId=${encodeURIComponent(d.doctorProfileId)}`}
                          >
                            Xem lịch
                          </Link>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                {!items.length && (
                  <TableRow>
                    <TableCell
                      colSpan={5}
                      className="h-24 text-center text-muted-foreground"
                    >
                      Không tìm thấy hồ sơ bác sĩ. Hãy đổi bộ lọc hoặc tạo hồ
                      sơ.
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
        <DoctorEditor
          doctor={editing === "new" ? undefined : editing}
          close={() => setEditing(null)}
          saved={() => {
            setEditing(null);
            remote.refresh();
          }}
        />
      )}
    </>
  );
}

function DoctorEditor({
  doctor,
  close,
  saved,
}: {
  doctor?: AdminDoctor;
  close: () => void;
  saved: () => void;
}) {
  const accounts = useAdminRemoteData(doctor ? noAccounts : loadAccounts);
  const [accountId, setAccountId] = useState("");
  const [draft, setDraft] = useState<DoctorProfileInput>(doctor ?? empty);
  const [directory, setDirectory] = useState({
    publiclyVisible: doctor?.publiclyVisible ?? false,
    acceptingNewBookings: doctor?.acceptingNewBookings ?? false,
  });
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [profileSaved, setProfileSaved] = useState(false);
  const input = (
    key: keyof DoctorProfileInput,
    label: string,
    required = false,
    multiline = false,
  ) => (
    <Field
      id={`doctor-${key}`}
      label={`${label}${required ? " *" : ""}`}
      key={key}
    >
      {multiline ? (
        <Textarea
          id={`doctor-${key}`}
          value={draft[key] ?? ""}
          onChange={(e) => setDraft({ ...draft, [key]: e.target.value })}
        />
      ) : (
        <Input
          id={`doctor-${key}`}
          required={required}
          maxLength={
            key === "fullName" ? 160 : key === "workContact" ? 254 : 2048
          }
          value={draft[key] ?? ""}
          onChange={(e) => setDraft({ ...draft, [key]: e.target.value })}
        />
      )}
    </Field>
  );
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !pending) close();
      }}
    >
      <DialogContent
        className="max-h-[90dvh] overflow-y-auto sm:max-w-2xl"
        showCloseButton={!pending}
      >
        <DialogHeader>
          <DialogTitle>
            {doctor ? "Hồ sơ bác sĩ" : "Tạo hồ sơ bác sĩ"}
          </DialogTitle>
          <DialogDescription>
            {doctor
              ? "Cập nhật thông tin chuyên môn và việc nhận lịch mới."
              : "Liên kết với một tài khoản bác sĩ đã có. Hồ sơ mới chưa công khai và chưa nhận lịch."}
          </DialogDescription>
        </DialogHeader>
        <form
          className="space-y-4"
          onSubmit={async (e) => {
            e.preventDefault();
            if (pending) return;
            if (!draft.fullName.trim() || (!doctor && !accountId)) {
              setError("Chọn tài khoản và nhập họ tên bác sĩ.");
              return;
            }
            if (draft.photoUrl && !/^https?:\/\//i.test(draft.photoUrl)) {
              setError(
                "Đường dẫn ảnh phải bắt đầu bằng https:// hoặc http://.",
              );
              return;
            }
            setPending(true);
            setError("");
            setProfileSaved(false);
            try {
              // Explicit projection: accountId and directory flags never leak into PUT.
              const body = Object.fromEntries(
                Object.keys(empty).map((key) => [
                  key,
                  draft[key as keyof DoctorProfileInput]?.trim() ?? "",
                ]),
              ) as DoctorProfileInput;
              const updated = await saveDoctorProfile(
                doctor?.doctorProfileId,
                accountId,
                body,
              );
              setProfileSaved(true);
              if (
                doctor &&
                (doctor.publiclyVisible !== directory.publiclyVisible ||
                  doctor.acceptingNewBookings !==
                    directory.acceptingNewBookings)
              )
                await saveDoctorDirectory(updated, directory);
              toast.success("Đã lưu hồ sơ bác sĩ.");
              saved();
            } catch (failure) {
              setError(adminApiMessage(failure));
            } finally {
              setPending(false);
            }
          }}
        >
          <fieldset disabled={pending} className="space-y-4">
            {!doctor && (
              <>
                <RemoteDataState
                  loading={accounts.loading}
                  error={accounts.error}
                  retry={accounts.refresh}
                />
                <Field id="doctor-account" label="Tài khoản bác sĩ *">
                  <NativeSelect
                    id="doctor-account"
                    className="w-full"
                    required
                    value={accountId}
                    onChange={(e) => {
                      setAccountId(e.target.value);
                      const a = accounts.data?.find(
                        (a) => a.accountId === e.target.value,
                      );
                      if (a) setDraft({ ...draft, fullName: a.fullName ?? "" });
                    }}
                  >
                    <option value="">Chọn tài khoản chưa có hồ sơ</option>
                    {accounts.data?.map((a) => (
                      <option key={a.accountId} value={a.accountId}>
                        {a.fullName || a.username} · {a.username}
                      </option>
                    ))}
                  </NativeSelect>
                </Field>
                {accounts.data?.length === 0 && (
                  <p className="text-sm text-muted-foreground">
                    Chưa có tài khoản bác sĩ đang hoạt động chưa liên kết hồ sơ.
                  </p>
                )}
              </>
            )}
            <div className="grid gap-4 sm:grid-cols-2">
              {input("fullName", "Họ tên", true)}
              {input("workContact", "Liên hệ công việc")}
            </div>
            {input("photoUrl", "Đường dẫn ảnh đại diện")}
            <div className="grid gap-4 sm:grid-cols-2">
              {input("specialties", "Chuyên khoa")}
              {input("qualifications", "Trình độ")}
            </div>
            {input("biography", "Tiểu sử", false, true)}
            {input("consultationInformation", "Thông tin khám", false, true)}
            {doctor && (
              <div className="grid gap-4 border-t pt-4 sm:grid-cols-2">
                <Field id="doctor-public" label="Hiển thị công khai">
                  <NativeSelect
                    id="doctor-public"
                    disabled={!doctor.inActiveDirectory}
                    value={String(directory.publiclyVisible)}
                    onChange={(e) =>
                      setDirectory({
                        ...directory,
                        publiclyVisible: e.target.value === "true",
                      })
                    }
                  >
                    <option value="false">Không</option>
                    <option value="true">Có</option>
                  </NativeSelect>
                </Field>
                <Field
                  id="doctor-booking"
                  label="Nhận lịch mới"
                  hint="Bác sĩ cần có ca trực và được phân công dịch vụ để có giờ trống."
                >
                  <NativeSelect
                    id="doctor-booking"
                    disabled={!doctor.inActiveDirectory}
                    value={String(directory.acceptingNewBookings)}
                    onChange={(e) =>
                      setDirectory({
                        ...directory,
                        acceptingNewBookings: e.target.value === "true",
                      })
                    }
                  >
                    <option value="false">Tạm dừng</option>
                    <option value="true">Đang nhận</option>
                  </NativeSelect>
                </Field>
              </div>
            )}
          </fieldset>
          {error && (
            <div role="alert" className="text-sm text-destructive">
              {profileSaved && (
                <p>
                  Thông tin hồ sơ đã lưu; trạng thái công khai / nhận lịch chưa
                  cập nhật.
                </p>
              )}
              <p>{error}</p>
            </div>
          )}
          <div className="flex justify-end gap-2">
            <Button
              type="button"
              variant="outline"
              disabled={pending}
              onClick={close}
            >
              Đóng
            </Button>
            <Button
              type="submit"
              disabled={
                pending ||
                (!doctor && (!accounts.data?.length || accounts.loading))
              }
            >
              {pending ? "Đang lưu..." : "Lưu hồ sơ"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
const noAccounts = async () => [];
