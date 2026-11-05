import { beforeEach, describe, expect, it } from "vitest";
import { doctorDemoApi, resetDoctorDemo } from "./doctorExamDemoApi";

// Bản demo mô phỏng các luật chính của BE; test này giữ cho bản xem thử không lệch luật R3.
beforeEach(() => {
  resetDoctorDemo();
});

const exam = {
  reasonForVisit: "Đau",
  symptoms: "Tê",
  examinationFindings: "Lasegue (+)",
  injuryBodySite: "",
  injurySide: "",
  injuryMechanism: "",
  diagnoses: [{ code: "M51.2", type: "PRIMARY" as const }],
};

describe("luồng khám trên bản demo", () => {
  it("hoàn tất khám bị chặn (MSG-86) cho đến khi có chẩn đoán chính và mọi chỉ định xong", async () => {
    const visitId = "demo-visit-2";
    const context = await doctorDemoApi.getVisitContext(visitId);

    await expect(doctorDemoApi.completeVisit(context)).rejects.toMatchObject({
      code: "MSG-86",
    });

    await doctorDemoApi.saveExamination(visitId, exam, "complete");
    // Vẫn chặn: X-quang đã có ảnh nhưng bác sĩ chưa kết luận hình ảnh.
    await expect(doctorDemoApi.completeVisit(context)).rejects.toMatchObject({
      code: "MSG-86",
    });

    const [order] = await doctorDemoApi.listOrders(visitId);
    expect(order.imaging?.reviewed).toBe(false);
    await doctorDemoApi.saveFindings(order, "Hẹp khe L4–L5");
    const [reviewed] = await doctorDemoApi.listOrders(visitId);
    expect(reviewed.imaging).toMatchObject({
      reviewed: true,
      findingsCompleted: false,
    });
    await doctorDemoApi.completeFindings(reviewed, "Hẹp khe L4–L5", null);

    await doctorDemoApi.completeVisit(context);
    expect((await doctorDemoApi.getVisitContext(visitId)).status).toBe(
      "COMPLETED",
    );
    expect(await doctorDemoApi.listResultSheets(visitId)).toEqual([
      expect.objectContaining({ version: 1, status: "CURRENT" }),
    ]);
  });

  it("sửa kết quả cần lý do, tạo phiên bản mới và đánh dấu bản cũ đã thay thế; chỉ với lượt đã hoàn tất", async () => {
    await expect(
      doctorDemoApi.amendResult("demo-visit-2", "Nhầm"),
    ).rejects.toMatchObject({
      code: "MSG-42",
    });
    await expect(
      doctorDemoApi.amendResult("demo-visit-4", " "),
    ).rejects.toMatchObject({
      code: "MSG-39",
    });
    const sheets = await doctorDemoApi.amendResult(
      "demo-visit-4",
      "Sửa chẩn đoán",
    );
    expect(sheets.map((s) => [s.version, s.status])).toEqual([
      [1, "REPLACED"],
      [2, "CURRENT"],
    ]);
  });

  it("khám phải có chẩn đoán chính mới hoàn thành; lời dặn cần bản ghi khám trước", async () => {
    await expect(
      doctorDemoApi.saveExamination(
        "demo-visit-1",
        { ...exam, diagnoses: [] },
        "complete",
      ),
    ).rejects.toMatchObject({ code: "MSG-08" });
    const advice = {
      homeCareAdvice: "Nghỉ",
      activityAdvice: "",
      rehabilitationAdvice: "",
      warningSigns: "",
      followUpRecommendation: "",
    };
    await expect(
      doctorDemoApi.saveAdvice("demo-visit-1", advice),
    ).rejects.toMatchObject({
      code: "MSG-41",
    });
    await doctorDemoApi.saveExamination("demo-visit-1", exam, "draft");
    expect(
      (await doctorDemoApi.saveAdvice("demo-visit-1", advice)).version,
    ).toBe(1);
  });

  it("chỉ định mới đi từ chờ thanh toán sang đã thanh toán rồi có kết quả theo thời gian; hủy được khi chưa có ảnh", async () => {
    const catalog = await doctorDemoApi.listCatalog();
    const lab = catalog.find((s) => s.kind === "LABORATORY")!;
    const [created] = await doctorDemoApi.submitOrders(
      "demo-visit-1",
      [
        {
          serviceId: lab.id,
          clinicalReason: "Viêm?",
          bodyArea: "",
          instructions: "",
        },
      ],
      catalog,
      "key",
    );
    expect(created).toMatchObject({
      status: "PENDING_PAYMENT",
      round: 1,
      kind: "LABORATORY",
    });
    expect((await doctorDemoApi.getVisitContext("demo-visit-1")).status).toBe(
      "WAITING_FOR_EXAMINATION",
    );

    await doctorDemoApi.cancelOrder(created, "CLINIC_UNABLE", "Thay chỉ định");
    const [after] = await doctorDemoApi.listOrders("demo-visit-1");
    expect(after.status).toBe("CANCELLED");
  });
});
