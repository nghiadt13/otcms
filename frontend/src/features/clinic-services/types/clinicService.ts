/** Public projection of GET /api/v1/clinic-services. */
export type PublicService = {
  id: string;
  name: string;
  description: string | null;
  /** Current clinic price as a decimal amount serialized as a JSON number. */
  price: number;
  durationMinutes: number;
  bufferMinutes: number;
  onlineBookable: boolean;
  active: boolean;
};

/** The backend catalog currently supports client-side name search only. */
export type PublicServiceQuery = {
  q?: string;
};
