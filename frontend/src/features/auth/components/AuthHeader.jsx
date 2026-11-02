import Link from "next/link";
import styles from "./auth.module.css";
const navigation = [
  "Find a doctor",
  "Services",
  "Patient reviews",
  "Clinic information",
];
export function AuthHeader({ actionHref, actionLabel }) {
  return (
    <header className={styles.siteHeader}>
      <Link
        className={styles.brand}
        href="/"
        aria-label="Orthopedic Trauma Clinic home"
      >
        <span className={styles.brandMark} aria-hidden="true" />
        <span>
          <span className={styles.brandName}>Orthopedic Trauma Clinic</span>
          <span className={styles.brandTagline}>
            Care that keeps you moving
          </span>
        </span>
      </Link>

      <nav className={styles.navLinks} aria-label="Primary navigation">
        {navigation.map((label) => (
          <Link href="/" key={label}>
            {label}
          </Link>
        ))}
      </nav>

      <div className={styles.headerActions}>
        <Link className={styles.helpLink} href="/">
          Help
        </Link>
        <Link className={styles.outlineButton} href={actionHref}>
          {actionLabel}
        </Link>
      </div>
    </header>
  );
}
