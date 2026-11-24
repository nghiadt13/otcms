"use client";

import { Star } from "lucide-react";
import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { useEligibleVisits } from "../hooks/useEligibleVisits";

export function VisitReviewAction({ visitId }: { visitId: string }) {
  const { items, loading, error } = useEligibleVisits();
  if (loading || error || !items.some((visit) => visit.visitId === visitId)) {
    return null;
  }

  return (
    <Link
      className={publicButtonClass("secondary")}
      href={PATIENT_ROUTES.newReview(visitId)}
    >
      <Star aria-hidden />
      Viết đánh giá
    </Link>
  );
}
