"use client";

import { useCallback, useState } from "react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { useAnnouncementList } from "../hooks/useAnnouncementList";
import { useAnnouncementOptions } from "../hooks/useAnnouncementOptions";
import { AnnouncementCreateDialog } from "./AnnouncementCreateDialog";
import { AnnouncementDetailSheet } from "./AnnouncementDetailSheet";
import { AnnouncementFilters } from "./AnnouncementFilters";
import { AnnouncementTable } from "./AnnouncementTable";

export function AnnouncementManagement() {
  const {
    list,
    searchInput,
    setSearchInput,
    range,
    changeRange,
    dateError,
    changeAudience,
    hasFilters,
  } = useAnnouncementList();
  const { options } = useAnnouncementOptions();
  const [createOpen, setCreateOpen] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const { reload } = list;
  const closeSheet = useCallback(() => setSelectedId(null), []);
  const closeCreate = useCallback(() => setCreateOpen(false), []);
  const canCreate = options?.allowedActions.includes("create") ?? false;

  return (
    <>
      <PageHeader
        title="Thông báo phòng khám"
        description="Gửi thông báo tới nhóm người nhận đã chọn qua email hoặc trong hệ thống."
        actions={
          canCreate ? (
            <Button onClick={() => setCreateOpen(true)}>Đăng thông báo</Button>
          ) : null
        }
      />
      <AnnouncementFilters
        searchInput={searchInput}
        onSearchChange={setSearchInput}
        audience={list.filters.audience}
        audiences={options ? options.audiences : null}
        onAudienceChange={changeAudience}
        range={range}
        onRangeChange={changeRange}
        dateError={dateError}
      />
      <AnnouncementTable
        data={list.data}
        loading={list.loading}
        error={list.error}
        hasFilters={hasFilters}
        page={list.page}
        onPageChange={list.setPage}
        onRetry={reload}
        onView={setSelectedId}
      />
      <AnnouncementDetailSheet
        id={selectedId}
        onClose={closeSheet}
        onUnavailable={reload}
      />
      {options ? (
        <AnnouncementCreateDialog
          open={createOpen}
          options={options}
          onClose={closeCreate}
          onCreated={reload}
        />
      ) : null}
    </>
  );
}
