import { AuthHeader } from "@/features/auth/components/AuthHeader";
import { AuthScaffold } from "@/features/auth/components/AuthScaffold";
import { RegisterForm } from "@/features/auth/components/RegisterForm";
import styles from "@/features/auth/components/auth.module.css";
export const metadata = {
  title: "Create account | Orthopedic Trauma Clinic",
  description:
    "Create a patient account for Orthopedic Trauma Clinic services.",
};
export default function RegisterPage() {
  return (
    <div className={styles.authScreen}>
      <AuthHeader actionHref="/login" actionLabel="Sign in" />
      <AuthScaffold
        calloutText="Email verification protects access before the patient profile is activated."
        calloutTitle="Your patient portal starts here"
        mode="register"
      >
        <RegisterForm />
      </AuthScaffold>
    </div>
  );
}
