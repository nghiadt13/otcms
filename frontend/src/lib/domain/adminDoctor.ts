export type AdminDoctor = {
  doctorProfileId: string;
  accountId: string;
  fullName: string;
  photoUrl: string | null;
  workContact: string | null;
  specialties: string;
  qualifications: string;
  biography: string;
  consultationInformation: string;
  publiclyVisible: boolean;
  acceptingNewBookings: boolean;
  inActiveDirectory: boolean;
};

export type DoctorInterval = {
  id: string;
  doctorProfileId: string;
  startsAt: string;
  endsAt: string;
};

export type MutationImpact = {
  blocked: boolean;
  affectedBookingCount: number;
  activeCareBlocks: boolean;
};
