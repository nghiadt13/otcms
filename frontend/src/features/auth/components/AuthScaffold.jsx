import Image from "next/image";
import styles from "./auth.module.css";
export function AuthScaffold({ children, mode, calloutTitle, calloutText }) {
  return (
    <main className={`${styles.pageShell} ${styles[`${mode}Shell`]}`}>
      <section className={`${styles.authPanel} ${styles[`${mode}Panel`]}`}>
        {children}
      </section>

      <aside
        className={styles.imagePanel}
        aria-label="Orthopedic clinic reception"
      >
        <Image
          alt="Bright orthopedic clinic reception and consultation corridor"
          className={`${styles.clinicPhoto} ${styles[`${mode}Photo`]}`}
          fill
          priority
          sizes={
            mode === "login"
              ? "(min-width: 761px) 59vw, 0px"
              : "(min-width: 821px) 45vw, 0px"
          }
          src="/images/clinic-interior.png"
        />
        <div className={styles.imageCallout}>
          <strong>{calloutTitle}</strong>
          <span>{calloutText}</span>
        </div>
      </aside>
    </main>
  );
}
