"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Activity,
  Bell,
  CalendarDays,
  ChevronRight,
  ClipboardList,
  FlaskConical,
  Hospital,
  LayoutDashboard,
  ListOrdered,
  Search,
  UserRound,
  ChevronsUpDown,
  LogOut,
} from "lucide-react";
import {
  fetchCurrentAccount,
  type CurrentAccount,
} from "@/lib/auth/currentAccountApi";
import { signOut } from "@/lib/auth/authSession";
import { ConfirmActionDialog } from "@/components/admin/ConfirmActionDialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { OtcmsBrandMark } from "@/components/brand/OtcmsBrandMark";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { LaboratoryLiveInfo } from "./LaboratoryLiveInfo";
import { LaboratoryOrders } from "./LaboratoryOrders";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarTrigger,
  useSidebar,
} from "@/components/ui/sidebar";

const orders = [
  {
    id: "XN-001",
    patient: "Nguyễn Minh An",
    code: "BN-001",
    age: 42,
    sex: "Nam",
    service: "Tổng phân tích tế bào máu",
    doctor: "BS. Trần Hoàng",
    reason: "Kiểm tra trước điều trị",
    status: "Đang chờ",
    number: "01",
  },
  {
    id: "XN-002",
    patient: "Trần Thu Hà",
    code: "BN-002",
    age: 36,
    sex: "Nữ",
    service: "Xét nghiệm đường huyết",
    doctor: "BS. Nguyễn Hà",
    reason: "Theo dõi sức khỏe",
    status: "Đang chờ",
    number: "02",
  },
  {
    id: "XN-003",
    patient: "Lê Quốc Bảo",
    code: "BN-003",
    age: 51,
    sex: "Nam",
    service: "Tổng phân tích tế bào máu",
    doctor: "BS. Trần Hoàng",
    reason: "Kiểm tra sức khỏe",
    status: "Đã có kết quả",
    number: "03",
  },
];
const indicators = [
  {
    name: "Hồng cầu (RBC)",
    unit: "T/L",
    range: "4,2 – 5,9",
    min: 4.2,
    max: 5.9,
  },
  { name: "Bạch cầu (WBC)", unit: "G/L", range: "4,0 – 10,0", min: 4, max: 10 },
  {
    name: "Huyết sắc tố (HGB)",
    unit: "g/L",
    range: "130 – 175",
    min: 130,
    max: 175,
  },
  {
    name: "Tiểu cầu (PLT)",
    unit: "G/L",
    range: "150 – 400",
    min: 150,
    max: 400,
  },
];
type Screen =
  | "Tổng quan"
  | "Danh sách chờ"
  | "Trạng thái chỉ định"
  | "Phòng dịch vụ"
  | "Lịch làm việc"
  | "Thông báo"
  | "Tài khoản của tôi";
const groups: {
  label: string;
  items: { title: Screen; icon: typeof Activity }[];
}[] = [
  {
    label: "Công việc",
    items: [
      { title: "Tổng quan", icon: LayoutDashboard },
      { title: "Danh sách chờ", icon: ListOrdered },
      { title: "Trạng thái chỉ định", icon: Activity },
    ],
  },
  {
    label: "Thông tin công việc",
    items: [
      { title: "Phòng dịch vụ", icon: Hospital },
      { title: "Lịch làm việc", icon: CalendarDays },
      { title: "Thông báo", icon: Bell },
    ],
  },
  {
    label: "Cá nhân",
    items: [{ title: "Tài khoản của tôi", icon: UserRound }],
  },
];
function isOrdersScreen(screen: Screen): boolean {
  return (
    screen === "Tổng quan" ||
    screen === "Danh sách chờ" ||
    screen === "Trạng thái chỉ định"
  );
}
function Navigation({
  screen,
  change,
  onExit,
}: {
  screen: Screen;
  change: (screen: Screen) => void;
  onExit: () => void;
}) {
  const { setOpenMobile, isMobile } = useSidebar();
  const [account, setAccount] = useState<CurrentAccount | null>(null);
  useEffect(() => {
    let active = true;
    fetchCurrentAccount()
      .then((value) => {
        if (active) setAccount(value);
      })
      .catch(() => {
        if (active) setAccount(null);
      });
    return () => {
      active = false;
    };
  }, []);
  const name = account?.username || account?.displayName || "Tài khoản";
  return (
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              size="lg"
              onClick={() => change("Tổng quan")}
              tooltip="OTCMS · Xét nghiệm"
            >
              <OtcmsBrandMark className="size-8" />
              <span className="grid text-left">
                <span className="font-semibold">OTCMS</span>
                <span className="text-xs text-muted-foreground">
                  Khu vực kỹ thuật viên
                </span>
              </span>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        {groups.map((group) => (
          <SidebarGroup key={group.label}>
            <SidebarGroupLabel>{group.label}</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                {group.items.map(({ title, icon: Icon }) => (
                  <SidebarMenuItem key={title}>
                    <SidebarMenuButton
                      isActive={screen === title}
                      tooltip={title}
                      onClick={() => {
                        change(title);
                        setOpenMobile(false);
                      }}
                    >
                      <Icon aria-hidden />
                      <span>{title}</span>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        ))}
      </SidebarContent>
      <SidebarFooter>
        <SidebarMenu>
          <SidebarMenuItem>
            <DropdownMenu>
              <DropdownMenuTrigger
                render={<SidebarMenuButton size="lg" tooltip={name} />}
              >
                <Avatar className="size-8 rounded-lg">
                  <AvatarFallback>
                    {name.slice(0, 2).toUpperCase()}
                  </AvatarFallback>
                </Avatar>
                <span className="grid flex-1 text-left leading-tight">
                  <span className="truncate text-sm font-medium">{name}</span>
                  <span className="truncate text-xs text-muted-foreground">
                    Kỹ thuật viên xét nghiệm
                  </span>
                </span>
                <ChevronsUpDown className="ml-auto size-4" />
              </DropdownMenuTrigger>
              <DropdownMenuContent
                className="w-64"
                side={isMobile ? "bottom" : "right"}
                align="end"
                sideOffset={8}
              >
                <div className="px-2 py-1.5 text-sm">
                  <strong>{name}</strong>
                  <p className="text-xs text-muted-foreground">
                    Kỹ thuật viên xét nghiệm
                  </p>
                </div>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  onClick={() => {
                    change("Tài khoản của tôi");
                    setOpenMobile(false);
                  }}
                >
                  <UserRound /> Hồ sơ tài khoản
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem variant="destructive" onClick={onExit}>
                  <LogOut /> Đăng xuất
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>
    </Sidebar>
  );
}
export function LaboratoryPreview() {
  const router = useRouter();
  const [exitOpen, setExitOpen] = useState(false);
  const [screen, setScreen] = useState<Screen>("Tổng quan");
  const [selected, setSelected] = useState<(typeof orders)[number] | null>(
    null,
  );
  const [query, setQuery] = useState("");
  const [values, setValues] = useState<string[]>(indicators.map(() => ""));
  const [approved, setApproved] = useState(false);
  const [attempted, setAttempted] = useState(false);
  const open = (order: (typeof orders)[number]) => {
    setSelected(order);
    setValues(indicators.map(() => ""));
    setApproved(false);
    setAttempted(false);
  };
  const change = (next: Screen) => {
    setScreen(next);
    setSelected(null);
  };
  const filtered = orders.filter(
    (o) =>
      (screen === "Trạng thái chỉ định" || o.status === "Đang chờ") &&
      `${o.id} ${o.patient}`
        .toLocaleLowerCase("vi")
        .includes(query.toLocaleLowerCase("vi")),
  );
  return (
    <SidebarProvider>
      <Navigation
        screen={screen}
        change={change}
        onExit={() => setExitOpen(true)}
      />
      <SidebarInset className="min-w-0">
        <header className="flex h-14 items-center gap-3 border-b px-4">
          <SidebarTrigger aria-label="Mở hoặc thu gọn menu" />
          <span className="text-sm font-medium">
            {selected ? "Chi tiết chỉ định" : screen}
          </span>
          <span className="ml-auto text-sm text-muted-foreground">
            Phòng xét nghiệm
          </span>
        </header>
        <main className="min-w-0 flex-1 p-4 md:p-6">
          <div className="mx-auto max-w-6xl space-y-6">
            {isOrdersScreen(screen) ? (
              <>
                <h1 className="text-2xl font-semibold">{screen}</h1>
                <LaboratoryOrders
                  key={screen}
                  screen={
                    screen as
                      "Tổng quan" | "Danh sách chờ" | "Trạng thái chỉ định"
                  }
                />
              </>
            ) : selected ? (
              <>
                <button
                  className="text-sm font-medium text-primary hover:underline"
                  onClick={() => setSelected(null)}
                >
                  ← Quay lại danh sách
                </button>
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div>
                    <h1 className="text-2xl font-semibold tracking-tight">
                      {selected.service}
                    </h1>
                    <p className="mt-2 text-sm text-muted-foreground">
                      Chỉ định {selected.id} · {selected.status}
                    </p>
                  </div>
                  <span className="rounded-md bg-secondary px-3 py-2 text-sm font-medium text-primary">
                    {selected.patient} · {selected.code}
                  </span>
                </div>
                <section className="rounded-lg border bg-card p-5">
                  <h2 className="font-semibold">Thông tin chỉ định</h2>
                  <dl className="mt-4 grid gap-4 text-sm sm:grid-cols-2 lg:grid-cols-4">
                    <div>
                      <dt className="text-muted-foreground">Người bệnh</dt>
                      <dd className="mt-1 font-medium">{selected.patient}</dd>
                    </div>
                    <div>
                      <dt className="text-muted-foreground">
                        Tuổi · Giới tính
                      </dt>
                      <dd className="mt-1 font-medium">
                        {selected.age} tuổi · {selected.sex}
                      </dd>
                    </div>
                    <div>
                      <dt className="text-muted-foreground">Bác sĩ chỉ định</dt>
                      <dd className="mt-1 font-medium">{selected.doctor}</dd>
                    </div>
                    <div>
                      <dt className="text-muted-foreground">Lý do lâm sàng</dt>
                      <dd className="mt-1 font-medium">{selected.reason}</dd>
                    </div>
                  </dl>
                </section>
                <section className="rounded-lg border bg-card">
                  <div className="border-b px-5 py-4">
                    <h2 className="text-lg font-semibold">
                      Nhập kết quả xét nghiệm
                    </h2>
                    <p className="mt-1 text-sm text-muted-foreground">
                      Nhập kết quả từ máy xét nghiệm và kiểm tra trước khi
                      duyệt.
                    </p>
                  </div>
                  {selected.id !== "XN-001" ? (
                    <p className="p-5 text-sm text-muted-foreground">
                      Dữ liệu chỉ số mẫu chỉ có ở chỉ định XN-001.
                    </p>
                  ) : (
                    <>
                      <div className="overflow-x-auto">
                        <table className="w-full min-w-[640px] text-left text-sm">
                          <thead className="bg-muted/50 text-muted-foreground">
                            <tr>
                              <th className="px-5 py-3 font-medium">Chỉ số</th>
                              <th className="px-3 py-3 font-medium">Kết quả</th>
                              <th className="px-3 py-3 font-medium">Đơn vị</th>
                              <th className="px-3 py-3 font-medium">
                                Khoảng tham chiếu
                              </th>
                              <th className="px-3 py-3 font-medium">
                                Đánh giá
                              </th>
                            </tr>
                          </thead>
                          <tbody className="divide-y">
                            {indicators.map((item, i) => {
                              const value = Number(values[i].replace(",", "."));
                              const invalid =
                                attempted &&
                                (!values[i].trim() || !Number.isFinite(value));
                              const flag =
                                values[i].trim() &&
                                Number.isFinite(value) &&
                                (value < item.min || value > item.max);
                              return (
                                <tr key={item.name}>
                                  <th className="px-5 py-4 font-medium">
                                    {item.name}
                                  </th>
                                  <td className="px-3 py-4">
                                    <Input
                                      aria-label={`Kết quả ${item.name}`}
                                      aria-invalid={invalid || undefined}
                                      disabled={approved}
                                      className="w-28"
                                      value={values[i]}
                                      onChange={(event) =>
                                        setValues(
                                          values.map((v, index) =>
                                            index === i
                                              ? event.target.value
                                              : v,
                                          ),
                                        )
                                      }
                                    />
                                    {invalid && (
                                      <p className="mt-1 max-w-36 text-xs text-destructive">
                                        Giá trị chỉ số không hợp lệ. Vui lòng
                                        kiểm tra lại giá trị và đơn vị.
                                      </p>
                                    )}
                                  </td>
                                  <td className="px-3 py-4">{item.unit}</td>
                                  <td className="px-3 py-4 tabular-nums">
                                    {item.range}
                                  </td>
                                  <td className="px-3 py-4">
                                    {flag ? (
                                      <span className="font-medium text-destructive">
                                        Ngoài khoảng
                                      </span>
                                    ) : values[i].trim() &&
                                      Number.isFinite(value) ? (
                                      "Trong khoảng"
                                    ) : (
                                      "—"
                                    )}
                                  </td>
                                </tr>
                              );
                            })}
                          </tbody>
                        </table>
                      </div>
                      <div className="flex flex-wrap items-center justify-between gap-3 border-t px-5 py-4">
                        <p className="text-sm text-muted-foreground">
                          Khoảng tham chiếu và chỉ số trên đây là dữ liệu mẫu.
                        </p>
                        <Button
                          disabled={approved}
                          onClick={() => {
                            setAttempted(true);
                            if (
                              values.every(
                                (v) =>
                                  v.trim() &&
                                  Number.isFinite(Number(v.replace(",", "."))),
                              )
                            )
                              setApproved(true);
                          }}
                        >
                          {approved ? "Đã duyệt bản mẫu" : "Duyệt kết quả mẫu"}
                        </Button>
                      </div>
                      {approved && (
                        <p
                          role="status"
                          className="px-5 pb-4 text-sm text-primary"
                        >
                          Đã duyệt trong bản xem thử. Không có dữ liệu nào được
                          gửi đi.
                        </p>
                      )}
                    </>
                  )}
                </section>
              </>
            ) : (
              <>
                <div>
                  <h1 className="text-2xl font-semibold tracking-tight">
                    {screen}
                  </h1>
                  <p className="mt-2 text-sm text-muted-foreground">
                    {screen === "Tổng quan"
                      ? "Công việc của phòng xét nghiệm hôm nay"
                      : screen === "Danh sách chờ"
                        ? "Người bệnh và chỉ định tại phòng xét nghiệm"
                        : "Thông tin dành cho kỹ thuật viên xét nghiệm"}
                  </p>
                </div>
                {screen === "Tổng quan" ||
                screen === "Danh sách chờ" ||
                screen === "Trạng thái chỉ định" ? (
                  <>
                    {screen === "Tổng quan" && (
                      <div className="grid gap-4 sm:grid-cols-3">
                        {[
                          { label: "Đang chờ", value: "2", icon: ListOrdered },
                          {
                            label: "Chỉ định hôm nay",
                            value: "3",
                            icon: ClipboardList,
                          },
                          {
                            label: "Đã có kết quả",
                            value: "1",
                            icon: FlaskConical,
                          },
                        ].map(({ label, value, icon: Icon }) => (
                          <div
                            key={label}
                            className="rounded-lg border bg-card p-5"
                          >
                            <div className="flex items-center justify-between text-muted-foreground">
                              <span className="text-sm font-medium">
                                {label}
                              </span>
                              <Icon
                                className="size-5 text-primary"
                                aria-hidden
                              />
                            </div>
                            <p className="mt-3 text-3xl font-semibold tabular-nums">
                              {value}
                            </p>
                          </div>
                        ))}
                      </div>
                    )}
                    <section className="overflow-hidden rounded-lg border bg-card">
                      <div className="flex flex-wrap items-center justify-between gap-3 border-b p-5">
                        <div>
                          <h2 className="text-lg font-semibold">
                            {screen === "Trạng thái chỉ định"
                              ? "Chỉ định xét nghiệm"
                              : "Danh sách chờ xét nghiệm"}
                          </h2>
                          <p className="mt-1 text-sm text-muted-foreground">
                            Chọn chỉ định để xem thông tin và nhập kết quả
                          </p>
                        </div>
                        <div className="relative w-full sm:w-72">
                          <Search
                            aria-hidden
                            className="absolute left-3 top-3 size-4 text-muted-foreground"
                          />
                          <Input
                            aria-label="Tìm theo mã chỉ định hoặc tên người bệnh"
                            className="pl-9"
                            placeholder="Tìm mã chỉ định, người bệnh"
                            value={query}
                            onChange={(e) => setQuery(e.target.value)}
                          />
                        </div>
                      </div>
                      <div className="divide-y">
                        {filtered.length ? (
                          filtered.map((order) => (
                            <button
                              key={order.id}
                              className="flex w-full items-center gap-4 px-5 py-4 text-left transition-colors hover:bg-secondary/50 focus-visible:outline-2 focus-visible:outline-primary"
                              onClick={() => open(order)}
                            >
                              <span className="flex size-10 shrink-0 items-center justify-center rounded-md bg-secondary font-semibold text-primary tabular-nums">
                                {order.number}
                              </span>
                              <span className="min-w-0 flex-1">
                                <span className="block font-semibold">
                                  {order.patient}
                                </span>
                                <span className="mt-1 block text-sm text-muted-foreground">
                                  {order.id} · {order.service}
                                </span>
                              </span>
                              <span className="hidden text-sm text-muted-foreground sm:block">
                                {order.status}
                              </span>
                              <ChevronRight
                                aria-hidden
                                className="size-4 shrink-0 text-muted-foreground"
                              />
                            </button>
                          ))
                        ) : (
                          <p className="p-5 text-sm text-muted-foreground">
                            Không tìm thấy chỉ định phù hợp.
                          </p>
                        )}
                      </div>
                    </section>
                  </>
                ) : screen === "Phòng dịch vụ" ||
                  screen === "Thông báo" ||
                  screen === "Tài khoản của tôi" ? (
                  <LaboratoryLiveInfo key={screen} screen={screen} />
                ) : (
                  <section className="rounded-lg border bg-card p-6">
                    <h2 className="font-semibold">{screen}</h2>
                    <p className="mt-2 text-sm text-muted-foreground">
                      Nội dung này sẽ được kết nối với dữ liệu của hệ thống.
                    </p>
                  </section>
                )}
              </>
            )}
          </div>
        </main>
      </SidebarInset>
      <ConfirmActionDialog
        open={exitOpen}
        onOpenChange={setExitOpen}
        title="Đăng xuất?"
        description="Bạn sẽ quay về màn đăng nhập nhân viên."
        confirmLabel="Đăng xuất"
        pendingLabel="Đang đăng xuất..."
        onConfirm={async () => {
          await signOut();
          router.replace("/staff-login");
        }}
      />
    </SidebarProvider>
  );
}
