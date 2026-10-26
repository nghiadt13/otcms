import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CalendarDays, Users } from "lucide-react";
import { describe, expect, it } from "vitest";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import {
  groupPatientNavItems,
  type PatientNavItem,
} from "../patientNavigation";
import { PatientPortalNav } from "./PatientPortalNav";

const items: PatientNavItem[] = [
  {
    label: "Lịch hẹn",
    href: PATIENT_ROUTES.appointments,
    icon: CalendarDays,
    group: "Khám bệnh",
  },
  {
    label: "Hồ sơ bệnh nhân",
    href: PATIENT_ROUTES.profiles,
    icon: Users,
    group: "Hồ sơ",
  },
];

describe("PatientPortalNav", () => {
  it("không có nhóm nào thì không render gì", () => {
    const { container } = render(<PatientPortalNav groups={[]} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("menu ngang: bấm nhóm thì xổ ra các mục đúng đường dẫn", async () => {
    render(<PatientPortalNav groups={groupPatientNavItems(items)} />);
    await userEvent.click(screen.getByRole("button", { name: "Khám bệnh" }));

    const lichHen = await screen.findByRole("menuitem", { name: "Lịch hẹn" });
    expect(lichHen).toHaveAttribute("href", "/portal/appointments");
    expect(
      screen.queryByRole("menuitem", { name: "Hồ sơ bệnh nhân" }),
    ).toBeNull();
  });

  it("Sheet màn hẹp liệt kê theo nhóm và đóng sau khi chọn mục", async () => {
    render(<PatientPortalNav groups={groupPatientNavItems(items)} />);
    await userEvent.click(screen.getByRole("button", { name: "Menu" }));

    const sheet = await screen.findByRole("dialog");
    expect(within(sheet).getByText("Khám bệnh")).toBeInTheDocument();
    expect(within(sheet).getByText("Hồ sơ")).toBeInTheDocument();
    const link = within(sheet).getByRole("link", { name: "Hồ sơ bệnh nhân" });
    expect(link).toHaveAttribute("href", "/portal/profiles");

    await userEvent.click(link);
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  });
});
