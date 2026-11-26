"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Activity,
  Bell,
  CalendarDays,
  ChevronsUpDown,
  Hospital,
  LayoutDashboard,
  ListOrdered,
  LogOut,
  UserRound,
} from "lucide-react";
import { LaboratoryLiveInfo } from "./LaboratoryLiveInfo";
import { LaboratoryOrders } from "./LaboratoryOrders";
import { LaboratorySchedule } from "./LaboratorySchedule";
import { signOut } from "@/lib/auth/authSession";
import { ConfirmActionDialog } from "@/components/admin/ConfirmActionDialog";
import {
  fetchCurrentAccount,
  type CurrentAccount,
} from "@/lib/auth/currentAccountApi";
import { OtcmsBrandMark } from "@/components/brand/OtcmsBrandMark";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
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
  { label: "Cá nhân", items: [{ title: "Tài khoản của tôi", icon: UserRound }] },
];

function isOrdersScreen(
  screen: Screen,
): screen is "Tổng quan" | "Danh sách chờ" | "Trạng thái chỉ định" {
  return (
    screen === "Tổng quan" ||
    screen === "Danh sách chờ" ||
    screen === "Trạng thái chỉ định"
  );
}

const screenViews: Record<Screen, string> = {
  "Tổng quan": "overview",
  "Danh sách chờ": "queue",
  "Trạng thái chỉ định": "history",
  "Phòng dịch vụ": "rooms",
  "Lịch làm việc": "schedule",
  "Thông báo": "notifications",
  "Tài khoản của tôi": "account",
};

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
                  <AvatarFallback>{name.slice(0, 2).toUpperCase()}</AvatarFallback>
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

export function LaboratoryPreview({
  initialScreen = "Tổng quan",
  orderId,
}: {
  initialScreen?: Screen;
  orderId?: string;
}) {
  const router = useRouter();
  const [exitOpen, setExitOpen] = useState(false);
  const [screen, setScreen] = useState<Screen>(initialScreen);
  const change = (next: Screen) => {
    if (orderId) {
      router.push(`/laboratory?view=${screenViews[next]}`);
      return;
    }
    setScreen(next);
  };

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
            {orderId ? "Chi tiết chỉ định" : screen}
          </span>
          <span className="ml-auto text-sm text-muted-foreground">
            Phòng xét nghiệm
          </span>
        </header>
        <main className="min-w-0 flex-1 p-4 md:p-6">
          <div className="mx-auto max-w-6xl space-y-6">
            {isOrdersScreen(screen) ? (
              <>
                <h1 className="text-2xl font-semibold">
                  {orderId
                    ? "Chi tiết chỉ định"
                    : screen === "Tổng quan"
                      ? "Tổng quan công việc"
                      : screen}
                </h1>
                <LaboratoryOrders
                  key={`${screen}:${orderId ?? "list"}`}
                  screen={screen}
                  onNavigate={change}
                  orderId={orderId}
                />
              </>
            ) : (
              <>
                <div>
                  <h1 className="text-2xl font-semibold tracking-tight">
                    {screen}
                  </h1>
                  <p className="mt-2 text-sm text-muted-foreground">
                    {screen === "Lịch làm việc"
                      ? "Ca làm việc được phân công tại phòng xét nghiệm."
                      : screen === "Phòng dịch vụ"
                        ? "Phòng xét nghiệm và trạng thái hoạt động hiện tại."
                        : screen === "Thông báo"
                          ? "Thông báo trong tài khoản của bạn."
                          : "Thông tin tài khoản của bạn."}
                  </p>
                </div>
                {screen === "Lịch làm việc" ? (
                  <LaboratorySchedule />
                ) : (
                  <LaboratoryLiveInfo key={screen} screen={screen} />
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
