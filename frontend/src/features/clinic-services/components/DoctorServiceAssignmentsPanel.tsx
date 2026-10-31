"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { notifyError } from "@/lib/messages/notify";
import {
  assignDoctorToService,
  listActiveDoctorOptions,
  listDoctorServiceAssignments,
  type ActiveDoctorOption,
  type DoctorServiceAssignment,
} from "../api/doctorServiceAssignmentsApi";

export function DoctorServiceAssignmentsPanel({
  serviceId,
}: {
  serviceId: string;
}) {
  const [reloadToken, setReloadToken] = useState(0);
  const requestKey = `${serviceId}:${reloadToken}`;
  const [loaded, setLoaded] = useState<{
    key: string;
    doctors: ActiveDoctorOption[];
    assignments: DoctorServiceAssignment[];
    error: boolean;
  } | null>(null);
  const [selectedDoctorId, setSelectedDoctorId] = useState("");
  const [pending, setPending] = useState(false);
  const loading = !loaded || loaded.key !== requestKey;
  const error = !loading && loaded?.error === true;
  const doctors = loaded?.key === requestKey && !error ? loaded.doctors : [];
  const assignments =
    loaded?.key === requestKey && !error ? loaded.assignments : [];

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([
      listActiveDoctorOptions(controller.signal),
      listDoctorServiceAssignments(serviceId, controller.signal),
    ])
      .then(([doctorOptions, currentAssignments]) => {
        if (controller.signal.aborted) return;
        setLoaded({
          key: requestKey,
          doctors: doctorOptions,
          assignments: currentAssignments,
          error: false,
        });
      })
      .catch((cause: unknown) => {
        if (controller.signal.aborted) return;
        setLoaded({
          key: requestKey,
          doctors: [],
          assignments: [],
          error: true,
        });
        notifyError(cause);
      });
    return () => controller.abort();
  }, [requestKey, serviceId]);

  const assignedIds = new Set(
    assignments.map((assignment) => assignment.doctorProfileId),
  );
  const availableDoctors = doctors.filter((doctor) => !assignedIds.has(doctor.id));

  async function assign() {
    if (!selectedDoctorId || pending) return;
    setPending(true);
    try {
      const assignment = await assignDoctorToService(
        serviceId,
        selectedDoctorId,
      );
      setLoaded((current) =>
        current?.key === requestKey
          ? { ...current, assignments: [...current.assignments, assignment] }
          : current,
      );
      setSelectedDoctorId("");
    } catch (cause) {
      notifyError(cause);
    } finally {
      setPending(false);
    }
  }

  return (
    <section className="space-y-3 border-t pt-4" aria-label="Bác sĩ phụ trách">
      <div>
        <h3 className="text-sm font-medium">Bác sĩ phụ trách</h3>
        <p className="mt-1 text-sm text-muted-foreground">
          Chỉ bác sĩ đang có hồ sơ hoạt động mới xuất hiện trong danh sách.
        </p>
      </div>
      {loading ? (
        <Skeleton className="h-9 w-full" />
      ) : error ? (
        <div className="space-y-2">
          <p role="alert" className="text-sm text-destructive">
            Chưa tải được danh sách bác sĩ phụ trách.
          </p>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => setReloadToken((token) => token + 1)}
          >
            Thử lại
          </Button>
        </div>
      ) : (
        <>
          {assignments.length > 0 ? (
            <ul className="space-y-1 text-sm">
              {assignments.map((assignment) => (
                <li key={assignment.id}>
                  {doctors.find(
                    (doctor) => doctor.id === assignment.doctorProfileId,
                  )?.displayName ?? "Bác sĩ không còn trong danh sách hoạt động"}
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-muted-foreground">
              Chưa gán bác sĩ cho dịch vụ này.
            </p>
          )}
          {availableDoctors.length > 0 ? (
            <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
              <div className="grid flex-1 gap-1.5">
                <Label htmlFor="doctor-service-assignment">Thêm bác sĩ</Label>
                <NativeSelect
                  id="doctor-service-assignment"
                  value={selectedDoctorId}
                  onChange={(event) => setSelectedDoctorId(event.target.value)}
                  disabled={pending}
                >
                  <option value="">Chọn bác sĩ</option>
                  {availableDoctors.map((doctor) => (
                    <option key={doctor.id} value={doctor.id}>
                      {doctor.displayName}
                    </option>
                  ))}
                </NativeSelect>
              </div>
              <Button
                type="button"
                disabled={!selectedDoctorId || pending}
                onClick={() => void assign()}
              >
                {pending ? "Đang gán..." : "Gán bác sĩ"}
              </Button>
            </div>
          ) : doctors.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              Tạo hồ sơ bác sĩ trước khi gán vào dịch vụ.
            </p>
          ) : null}
        </>
      )}
    </section>
  );
}
