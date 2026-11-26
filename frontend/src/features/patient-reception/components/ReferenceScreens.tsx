"use client";

import { useCallback, useState } from "react";
import { PageHeader } from "@/components/PageHeader";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Button } from "@/components/ui/button";
import { useAdminRemoteData } from "@/hooks/useAdminRemoteData";
import { messageFromError } from "@/lib/messages/appMessages";
import { listReceptionClinicServices } from "../api/receptionLiveApi";
import { normalizeText } from "../schema/receptionRules";
import { DataTable, money, SelectField, TextField } from "./ReceptionUi";

const categoryLabels: Record<string, string> = {
  EXAMINATION: "Khám chuyên khoa",
  IMAGING: "Chẩn đoán hình ảnh",
  LABORATORY: "Xét nghiệm",
};

export function ReferenceScreen() {
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("");
  const result = useAdminRemoteData(
    useCallback(
      (signal: AbortSignal) =>
        listReceptionClinicServices(signal).then((response) => response.items),
      [],
    ),
  );
  const services = (result.data ?? []).filter(
    (service) =>
      (!category || service.type === category) &&
      normalizeText(service.name).includes(normalizeText(search)),
  );

  return (
    <>
      <PageHeader
        title="Danh mục dịch vụ"
        description="Tra cứu dịch vụ và giá hiện hành từ danh mục của phòng khám."
      />
      <div className="mb-4 grid gap-3 sm:grid-cols-2">
        <TextField
          label="Tên dịch vụ"
          value={search}
          onChange={setSearch}
        />
        <SelectField
          label="Nhóm dịch vụ"
          value={category}
          onChange={setCategory}
          options={[
            { value: "", label: "Tất cả nhóm" },
            ...Object.entries(categoryLabels).map(([value, label]) => ({
              value,
              label,
            })),
          ]}
        />
      </div>
      {result.loading && <p role="status">Đang tải danh mục dịch vụ…</p>}
      {result.error != null && (
        <div role="alert" className="mb-4 space-y-2">
          <p>{messageFromError(result.error)}</p>
          <Button variant="outline" onClick={result.refresh}>
            Thử lại
          </Button>
        </div>
      )}
      <DataTable
        key={`${search}-${category}`}
        label="Danh mục dịch vụ"
        headers={["Dịch vụ", "Nhóm", "Thời lượng", "Đơn giá", "Trạng thái"]}
        rows={services.map((service) => ({
          id: service.id,
          cells: [
            <span key="name" className="font-medium">
              {service.name}
            </span>,
            categoryLabels[service.type] ?? service.type,
            `${service.durationMinutes} phút`,
            <span key="price" className="whitespace-nowrap tabular-nums">
              {money(service.price)}
            </span>,
            <StatusBadge
              key="status"
              tone={service.active ? "success" : "neutral"}
            >
              {service.active ? "Đang cung cấp" : "Ngừng cung cấp"}
            </StatusBadge>,
          ],
        }))}
        empty="Không tìm thấy dịch vụ phù hợp."
      />
    </>
  );
}
