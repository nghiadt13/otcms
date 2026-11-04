import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/apiError";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { PatientSearchScreen } from "./PatientScreens";

vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));
vi.mock("./ReceptionProvider", () => ({
  useReception: () => ({ pending: false }),
}));
vi.mock("@/lib/messages/notify", () => ({ notifySuccess: vi.fn() }));
const request = vi.mocked(apiRequest);
const patient = {
  patientProfileId: "patient-1",
  fullName: "Trần Văn Bình",
  phoneCanonical: "+84900000002",
  dateOfBirth: "1948-08-12",
};
const profile = {
  ...patient,
  phoneOwnerType: "PATIENT",
  gender: "MALE",
  address: "Hà Nội",
  identityDocumentType: null,
  nationalId: null,
  version: 2,
};

beforeEach(() => vi.resetAllMocks());

function search(keyword: string, dob?: string) {
  fireEvent.change(screen.getByLabelText("Tên hoặc số điện thoại"), {
    target: { value: keyword },
  });
  if (dob)
    fireEvent.change(screen.getByLabelText("Ngày sinh"), {
      target: { value: dob },
    });
  fireEvent.click(screen.getByRole("button", { name: "Tìm bệnh nhân" }));
}

it.each(["Bình", "binh", "0900000002", "+84 900 000 002"])(
  "gửi tra cứu %s đến backend",
  async (keyword) => {
    request.mockResolvedValueOnce({ content: [patient] });
    render(<PatientSearchScreen />);
    search(keyword);
    expect(await screen.findByText(patient.fullName)).toBeInTheDocument();
    expect(
      new URL(request.mock.calls[0][0], "http://example.test").searchParams.get(
        "query",
      ),
    ).toBe(keyword);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  },
);

it("đối chiếu chính xác số điện thoại và ngày sinh rồi lưu với version đã tải", async () => {
  request
    .mockResolvedValueOnce({ items: [patient] })
    .mockResolvedValueOnce(profile)
    .mockResolvedValueOnce({ ...profile, address: "Hải Phòng", version: 3 });
  render(<PatientSearchScreen />);
  search("0900000002", patient.dateOfBirth);
  fireEvent.click(await screen.findByRole("button", { name: "Mở hồ sơ" }));
  const address = await screen.findByLabelText("Địa chỉ");
  fireEvent.change(address, { target: { value: "Hải Phòng" } });
  fireEvent.click(screen.getByRole("button", { name: "Lưu hồ sơ" }));
  await waitFor(() => expect(request).toHaveBeenCalledTimes(3));
  expect(request.mock.calls[0][0]).toBe(
    "/api/v1/patient-profiles/matches?phone=0900000002&dateOfBirth=1948-08-12",
  );
  expect(request.mock.calls[2][0]).toBe("/api/v1/patient-profiles/patient-1");
  expect(JSON.parse(String(request.mock.calls[2][1]?.body))).toMatchObject({
    version: 2,
    address: "Hải Phòng",
  });
  expect(screen.queryByText(/dị ứng|tiền sử/i)).not.toBeInTheDocument();
});

it("tạo hồ sơ trực tiếp khi chưa tra cứu và truyền ID thật sang Tiếp nhận", async () => {
  request.mockResolvedValueOnce(patient).mockResolvedValueOnce(profile);
  render(<PatientSearchScreen />);
  expect(request).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Tạo hồ sơ" }));
  const dialog = screen.getByRole("dialog");
  for (const [label, value] of [
    ["Họ và tên", patient.fullName],
    ["Ngày sinh", patient.dateOfBirth],
    ["Giới tính", "MALE"],
    ["Số điện thoại", "0900000002"],
    ["Địa chỉ", "Hà Nội"],
  ])
    fireEvent.change(within(dialog).getByLabelText(label, { exact: true }), {
      target: { value },
    });
  fireEvent.click(within(dialog).getByRole("button", { name: "Lưu hồ sơ" }));
  expect(
    await screen.findByRole("link", { name: "Tiếp nhận" }),
  ).toHaveAttribute("href", "/reception?patientId=patient-1");
  expect(request.mock.calls[0][0]).toBe("/api/v1/patient-profiles");
  expect(JSON.parse(String(request.mock.calls[0][1]?.body))).not.toHaveProperty(
    "accountId",
  );
});

it("hiện lỗi khi đối chiếu trùng nhiều hồ sơ", async () => {
  request.mockRejectedValueOnce(
    new ApiError(409, { errorCode: "PATIENT_MATCH_AMBIGUOUS" }),
  );
  render(<PatientSearchScreen />);
  search("0900000002", patient.dateOfBirth);
  expect(await screen.findByRole("alert")).toHaveTextContent(
    formatAppMessage("MSG-18"),
  );
});

it("chỉ gửi xác nhận liên kết sau khi lễ tân xác nhận đã kiểm giấy tờ", async () => {
  request
    .mockResolvedValueOnce({
      items: [
        {
          ...patient,
          linkId: "link-1",
          relationship: "CHILD",
          nationalId: "123",
        },
      ],
    })
    .mockResolvedValueOnce({ result: "LINKED" });
  render(<PatientSearchScreen />);
  fireEvent.click(screen.getByRole("button", { name: "Xem yêu cầu chờ" }));
  fireEvent.click(await screen.findByRole("button", { name: "Xác minh" }));
  expect(request).toHaveBeenCalledTimes(1);
  fireEvent.click(
    screen.getByRole("button", { name: "Xác nhận đã kiểm giấy tờ" }),
  );
  expect(
    await screen.findByText("Không có yêu cầu đang chờ xác nhận."),
  ).toBeInTheDocument();
  expect(request).toHaveBeenLastCalledWith(
    "/api/v1/care-links/link-1/counter-confirmation",
    {
      method: "POST",
      body: JSON.stringify({ identityChecked: true }),
    },
  );
});
