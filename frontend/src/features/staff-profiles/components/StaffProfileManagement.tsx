"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { TablePagination } from "@/components/admin/TablePagination";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { useStaffProfileList } from "../hooks/useStaffProfileList";
import { StaffProfileCreateDialog } from "./StaffProfileCreateDialog";
import { StaffProfileDetailSheet } from "./StaffProfileDetailSheet";
import { StaffProfileFilters } from "./StaffProfileFilters";
import { StaffProfileTable } from "./StaffProfileTable";

export function StaffProfileManagement() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  // `?accountId=` (từ màn Tài khoản) chỉ đọc một lần để mở sẵn form tạo.
  const [initialAccountId] = useState(() => searchParams.get("accountId") ?? "");
  const [createOpen, setCreateOpen] = useState(initialAccountId !== "");
  const [sheet, setSheet] = useState<{ id: string; edit: boolean } | null>(null);
  const list = useStaffProfileList();

  useEffect(() => {
    // Bỏ tham số khỏi URL để F5 không mở lại form.
    if (initialAccountId) router.replace(pathname);
  }, [initialAccountId, pathname, router]);

  const { filters } = list;
  const filtered =
    filters.q !== "" ||
    filters.role !== "" ||
    filters.isPublic !== "" ||
    filters.status !== "ACTIVE";

  return (
    <>
      <PageHeader
        title="Hồ sơ nhân viên"
        description="Hồ sơ hiển thị nội bộ; bác sĩ bật công khai sẽ hiện trên website."
        actions={<Button onClick={() => setCreateOpen(true)}>Tạo hồ sơ</Button>}
      />
      <StaffProfileFilters
        search={list.search}
        onSearchChange={list.setSearch}
        filters={filters}
        onFilterChange={list.setFilter}
      />
      <StaffProfileTable
        items={list.data?.items ?? []}
        loading={list.loading}
        error={list.error}
        onRetry={list.reload}
        filtered={filtered}
        onView={(id) => setSheet({ id, edit: false })}
        onEdit={(id) => setSheet({ id, edit: true })}
      />
      <TablePagination
        page={list.page}
        totalPages={list.data?.totalPages ?? 1}
        totalItems={list.data?.totalItems ?? 0}
        onPageChange={list.setPage}
      />
      <StaffProfileCreateDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        initialAccountId={initialAccountId || undefined}
        onCreated={list.reload}
      />
      <StaffProfileDetailSheet
        profileId={sheet?.id ?? null}
        startInEditMode={sheet?.edit ?? false}
        onClose={() => setSheet(null)}
        onChanged={list.reload}
      />
    </>
  );
}
