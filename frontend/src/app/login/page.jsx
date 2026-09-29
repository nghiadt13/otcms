import { AuthHeader } from "@/features/auth/components/AuthHeader";
import { AuthScaffold } from "@/features/auth/components/AuthScaffold";
import { SignInForm } from "@/features/auth/components/SignInForm";
import styles from "@/features/auth/components/auth.module.css";
export const metadata = {
  title: "Sign in | Orthopedic Trauma Clinic",
  description: "Secure access to the Orthopedic Trauma Clinic patient portal.",
};
export default function LoginPage() {
  return (
    <div className={styles.authScreen}>
      <AuthHeader actionHref="/register" actionLabel="Create patient account" />
      <AuthScaffold
        calloutText="Browse doctors, services and public clinic information without signing in."
        calloutTitle="Orthopedic care in one place"
        mode="login"
      >
        <SignInForm />
      </AuthScaffold>
    </div>
  );
}
