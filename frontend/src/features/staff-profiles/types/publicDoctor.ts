// Public doctor profile (BR-09): only the professional information of an active doctor who has
// enabled public display. Free times (BR-20) are not part of the profile; they come from the booking
// availability API (see api/publicDoctorOpenTimesApi.ts).

export type PublicDoctor = {
  id: string;
  fullName: string;
  photoUrl: string | null;
  specialties: string[];
  qualifications: string;
  biography: string;
  consultationInfo: string;
};

/** One free time of a doctor for one bookable service (BR-20). */
export type DoctorOpenTime = {
  /** Service of this slot; booking must use the same service. */
  serviceId: string;
  /** Slot start as a UTC instant from the backend, e.g. "2026-11-18T02:00:00Z". */
  startAt: string;
};

/** One day of the 7-day window; an empty `times` list means "no free time that day". */
export type DoctorOpenDay = {
  /** YYYY-MM-DD, Vietnam time. */
  date: string;
  times: DoctorOpenTime[];
};
