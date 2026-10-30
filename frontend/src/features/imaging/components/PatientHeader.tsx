"use client";

import { PatientAvatar } from "./PatientAvatar";
import type { ImagingOrder } from "../types/imaging";

export function PatientHeader({ order }: { order: ImagingOrder }) {
  return (
    <div className="im-patient-header">
      <PatientAvatar name={order.patient} />
      <div>
        <h2>{order.patient}</h2>
        <p>
          {order.patientId} <span>·</span> {order.age} tuổi <span>·</span>{" "}
          {order.sex}
        </p>
      </div>
      <div className="im-patient-header-end">
        <span>Lượt khám</span>
        <strong>{order.visitId}</strong>
      </div>
    </div>
  );
}
