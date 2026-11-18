"use client";

import { useEffect, useMemo, useState } from "react";
import { apiRequest } from "@/lib/api/client";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

type Category = { id: string; code: string; name: string };
type Partner = { id: string; name: string; contact: string | null; active: boolean; categories: Category[] };
type StatusFilter = "ALL" | "ACTIVE" | "INACTIVE";

export function PartnersManagement() {
  const [items, setItems] = useState<Partner[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [name, setName] = useState("");
  const [contact, setContact] = useState("");
  const [categoryCodes, setCategoryCodes] = useState<string[]>([]);
  const [editing, setEditing] = useState<Partner | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");
  const [revision, setRevision] = useState(0);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    Promise.allSettled([
      apiRequest<{ items: Partner[] }>("/api/v1/admin/partners", { signal: controller.signal }),
      apiRequest<{ items: Category[] }>("/api/v1/admin/partner-categories", { signal: controller.signal }),
    ]).then(([partnerResult, categoryResult]) => {
      if (controller.signal.aborted) return;
      const errors: string[] = [];
      if (partnerResult.status === "fulfilled") setItems(partnerResult.value.items);
      else errors.push("Không thể tải danh sách đối tác.");
      if (categoryResult.status === "fulfilled") setCategories(categoryResult.value.items);
      else errors.push("Không thể tải danh mục lĩnh vực.");
      setError(errors.join(" "));
    }).finally(() => {
      if (!controller.signal.aborted) setLoading(false);
    });
    return () => controller.abort();
  }, [revision]);

  const visibleItems = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase("vi");
    return items.filter((item) => {
      const matchesQuery = !normalizedQuery || `${item.name} ${item.contact ?? ""}`.toLocaleLowerCase("vi").includes(normalizedQuery);
      const matchesCategory = !categoryFilter || item.categories.some((category) => category.code === categoryFilter);
      const matchesStatus = statusFilter === "ALL" || (statusFilter === "ACTIVE" ? item.active : !item.active);
      return matchesQuery && matchesCategory && matchesStatus;
    });
  }, [items, query, categoryFilter, statusFilter]);

  function openCreateForm() {
    setEditing(null);
    setName("");
    setContact("");
    setCategoryCodes([]);
    setError("");
    setFormOpen(true);
  }

  function closeForm() {
    setFormOpen(false);
    setEditing(null);
    setName("");
    setContact("");
    setCategoryCodes([]);
  }

  function openEditForm(partner: Partner) {
    setEditing(partner);
    setName(partner.name);
    setContact(partner.contact ?? "");
    setCategoryCodes(partner.categories.map((category) => category.code));
    setError("");
    setFormOpen(true);
  }

  async function save(partner: Partner | null, active: boolean, values = { name, contact, categoryCodes }) {
    if (busy || values.categoryCodes.length === 0) return;
    setBusy(true);
    setError("");
    try {
      await apiRequest(partner ? `/api/v1/admin/partners/${encodeURIComponent(partner.id)}` : "/api/v1/admin/partners", {
        method: partner ? "PUT" : "POST",
        body: JSON.stringify({ name: values.name.trim(), contact: values.contact.trim(), active, categoryCodes: values.categoryCodes }),
      });
      closeForm();
      setLoading(true);
      setRevision((value) => value + 1);
    } catch {
      setError("Không thể lưu đối tác. Vui lòng kiểm tra thông tin và thử lại.");
    } finally {
      setBusy(false);
    }
  }

  return <>
    <PageHeader title="Đối tác" description="Quản lý đối tác theo lĩnh vực để dùng chung trong các quy trình của phòng khám." />
    {error && <div role="alert" className="mb-4 flex flex-wrap items-center gap-3 text-destructive"><span>{error}</span><Button type="button" variant="outline" onClick={() => { setLoading(true); setRevision((value) => value + 1); }}>Thử tải lại</Button></div>}

    <section className="overflow-hidden rounded-lg border bg-card">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b p-4">
        <div><h2 className="font-semibold">Danh sách đối tác</h2><p className="text-sm text-muted-foreground">{visibleItems.length} đối tác</p></div>
        <Button type="button" onClick={openCreateForm}>Thêm đối tác</Button>
      </div>

      <div className="grid gap-3 border-b p-4 md:grid-cols-[minmax(220px,1fr)_220px_180px]">
        <Input aria-label="Tìm đối tác" placeholder="Tìm theo tên hoặc liên hệ" value={query} onChange={(event) => setQuery(event.target.value)} />
        <select aria-label="Lọc theo lĩnh vực" className="h-9 rounded-lg border border-input bg-background px-3 text-sm" value={categoryFilter} onChange={(event) => setCategoryFilter(event.target.value)}>
          <option value="">Tất cả lĩnh vực</option>
          {categories.map((category) => <option key={category.code} value={category.code}>{category.name}</option>)}
        </select>
        <select aria-label="Lọc theo trạng thái" className="h-9 rounded-lg border border-input bg-background px-3 text-sm" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as StatusFilter)}>
          <option value="ALL">Mọi trạng thái</option><option value="ACTIVE">Đang hoạt động</option><option value="INACTIVE">Ngừng hoạt động</option>
        </select>
      </div>

      {formOpen && <form className="grid gap-3 border-b bg-secondary/30 p-4 sm:grid-cols-2" onSubmit={(event) => { event.preventDefault(); void save(editing, editing?.active ?? true); }}>
        <h3 className="font-semibold sm:col-span-2">{editing ? "Cập nhật đối tác" : "Thông tin đối tác mới"}</h3>
        <label className="grid gap-1 text-sm">Tên đối tác<Input value={name} onChange={(event) => setName(event.target.value)} required maxLength={255} /></label>
        <label className="grid gap-1 text-sm">Thông tin liên hệ<Input value={contact} onChange={(event) => setContact(event.target.value)} maxLength={255} /></label>
        <fieldset className="sm:col-span-2">
          <legend className="mb-2 text-sm font-medium">Lĩnh vực</legend>
          <div className="flex flex-wrap gap-x-5 gap-y-2">
            {categories.map((category) => <label className="flex items-center gap-2 text-sm" key={category.code}>
              <input type="checkbox" checked={categoryCodes.includes(category.code)} onChange={(event) => setCategoryCodes((current) => event.target.checked ? [...current, category.code] : current.filter((code) => code !== category.code))} />
              {category.name}
            </label>)}
          </div>
        </fieldset>
        <div className="flex gap-2 sm:col-span-2"><Button type="submit" disabled={busy || !name.trim() || categoryCodes.length === 0}>{busy ? "Đang lưu..." : editing ? "Lưu thay đổi" : "Thêm đối tác"}</Button><Button type="button" variant="outline" disabled={busy} onClick={closeForm}>Hủy</Button></div>
      </form>}

      {loading ? <p className="p-5" role="status">Đang tải đối tác...</p> : visibleItems.length === 0 ? <p className="p-5">{items.length === 0 ? "Chưa có đối tác. Bấm Thêm đối tác để tạo mới." : "Không có đối tác phù hợp bộ lọc."}</p> : <div className="divide-y">
        {visibleItems.map((item) => <div className="flex flex-wrap items-center justify-between gap-3 p-4" key={item.id}>
          <div className="min-w-0"><strong>{item.name}</strong><p className="text-sm text-muted-foreground">{item.categories.map((category) => category.name).join(" · ")} · {item.contact || "Chưa có thông tin liên hệ"}</p></div>
          <div className="flex items-center gap-2"><span className="text-sm">{item.active ? "Đang hoạt động" : "Ngừng hoạt động"}</span><Button type="button" variant="outline" onClick={() => openEditForm(item)}>Sửa</Button><Button type="button" variant="outline" disabled={busy} onClick={() => void save(item, !item.active, { name: item.name, contact: item.contact ?? "", categoryCodes: item.categories.map((category) => category.code) })}>{item.active ? "Ngừng hoạt động" : "Kích hoạt"}</Button></div>
        </div>)}
      </div>}
    </section>
  </>;
}
