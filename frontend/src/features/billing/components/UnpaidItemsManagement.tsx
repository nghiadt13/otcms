"use client";

import { useState } from "react";
import { TablePagination } from "@/components/admin/TablePagination";
import { PageHeader } from "@/components/PageHeader";
import {
  useUnpaidItemServiceOptions,
  useUnpaidItems,
} from "../hooks/useUnpaidItems";
import { UnpaidItemDetailSheet } from "./UnpaidItemDetailSheet";
import { UnpaidItemFilterBar } from "./UnpaidItemFilterBar";
import { UnpaidItemsTable } from "./UnpaidItemsTable";

/** Màn Khoản chưa thanh toán: chỉ xem, không có nút chính. Thu tiền là việc của Lễ tân. */
export function UnpaidItemsManagement() {
  const list = useUnpaidItems();
  const services = useUnpaidItemServiceOptions();
  const [selected, setSelected] = useState<{
    id: string;
    itemName: string;
  } | null>(null);

  return (
    <>
      <PageHeader
        title="Khoản chưa thanh toán"
        description="Phí khám và chỉ định đang chờ thanh toán. Màn này chỉ xem."
      />
      <UnpaidItemFilterBar
        filters={list.filters}
        onChange={list.setFilters}
        serviceOptions={services.options}
        serviceOptionsFailed={services.error !== null}
      />
      <UnpaidItemsTable
        items={list.items}
        totalItems={list.totalItems}
        summary={list.summary}
        loading={list.loading}
        error={list.error}
        hasFilter={list.hasFilter}
        onRetry={list.reload}
        onView={(item) => setSelected({ id: item.id, itemName: item.itemName })}
      />
      {!list.loading && !list.error ? (
        <TablePagination
          page={list.page}
          totalPages={list.totalPages}
          totalItems={list.totalItems}
          onPageChange={list.setPage}
        />
      ) : null}
      {selected ? (
        <UnpaidItemDetailSheet
          itemId={selected.id}
          itemName={selected.itemName}
          onClose={() => setSelected(null)}
        />
      ) : null}
    </>
  );
}
