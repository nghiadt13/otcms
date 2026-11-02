import { beforeEach, describe, expect, it } from "vitest";
import { removeStaffProfile, updateStaffProfile } from "./staffProfileApi";
import {
  getPublicDoctor,
  listFeaturedDoctors,
  listPublicDoctors,
} from "./publicDoctorApi";
import {
  mockStore,
  resetStaffProfileMockStore,
  type MockStaffRecord,
} from "./staffProfileMockData";

const byUsername = (username: string): MockStaffRecord => {
  const record = mockStore.profiles.find((item) => item.username === username);
  if (!record) throw new Error(`Thiếu hồ sơ ${username} trong dữ liệu giả`);
  return record;
};

beforeEach(() => {
  resetStaffProfileMockStore();
});

describe("listPublicDoctors", () => {
  it("chỉ gồm bác sĩ đang hoạt động và đã bật hiển thị (BR-09)", async () => {
    const { items } = await listPublicDoctors();
    expect(items.map((d) => d.fullName)).toEqual([
      "BS. Nguyễn Văn Trung",
      "BS. Lê Minh Quân",
      "ThS.BS. Trần Thu Hà",
    ]);
  });

  it("danh sách không có availability", async () => {
    const { items } = await listPublicDoctors();
    expect(items.every((d) => !("availability" in d))).toBe(true);
  });
});

describe("getPublicDoctor", () => {
  it("bác sĩ tắt hiển thị hoặc đã gỡ trả 404 MSG-24", async () => {
    await expect(
      getPublicDoctor(byUsername("bs.yen").id),
    ).rejects.toMatchObject({
      status: 404,
      code: "MSG-24",
    });
    await expect(getPublicDoctor(byUsername("bs.an").id)).rejects.toMatchObject(
      {
        status: 404,
        code: "MSG-24",
      },
    );
  });

  it("trả 7 ngày giờ nhận khám liên tiếp, đúng ngày hết giờ đã cấu hình", async () => {
    const doctor = await getPublicDoctor(byUsername("bs.trung").id);
    expect(doctor.availability).toHaveLength(7);
    const dates = doctor.availability.map((day) => day.date);
    expect(dates[0]).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    for (let i = 1; i < dates.length; i += 1) {
      expect(dates[i] > dates[i - 1]).toBe(true);
    }
    const fullDays = doctor.availability
      .map((day, index) => (day.hasOpenTime ? null : index))
      .filter((index): index is number => index !== null);
    expect(fullDays).toEqual([1, 4]);
  });
});

describe("kho hồ sơ dùng chung với Admin", () => {
  it("bật hiển thị ở Admin thì xuất hiện ở danh bạ công khai; gỡ hồ sơ thì biến mất", async () => {
    const yen = byUsername("bs.yen");
    await updateStaffProfile(yen.id, {
      displayName: yen.displayName,
      workContact: yen.workContact,
      doctor: { ...yen.doctor!, isPublic: true },
    });
    expect((await listPublicDoctors()).items.map((d) => d.id)).toContain(
      yen.id,
    );
    await expect(getPublicDoctor(yen.id)).resolves.toMatchObject({
      id: yen.id,
    });

    await removeStaffProfile(byUsername("bs.minh").id);
    expect((await listPublicDoctors()).items.map((d) => d.id)).not.toContain(
      byUsername("bs.minh").id,
    );
  });
});

describe("listFeaturedDoctors", () => {
  it("lấy tối đa số bác sĩ yêu cầu từ danh bạ công khai", async () => {
    const featured = await listFeaturedDoctors(2);
    expect(featured).toHaveLength(2);
    expect(featured[0].fullName).toBe("BS. Nguyễn Văn Trung");
  });
});
