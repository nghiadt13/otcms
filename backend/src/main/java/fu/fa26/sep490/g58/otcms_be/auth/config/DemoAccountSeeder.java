package fu.fa26.sep490.g58.otcms_be.auth.config;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.PatientProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.Gender;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.PhoneOwnerType;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.PatientProfileRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.core.env.Environment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

/** Local/demo-only seed. It is not loaded unless the {@code demo} profile is active. */
@Configuration
@Profile("demo")
public class DemoAccountSeeder {
    @Bean
    public CommandLineRunner seedDemoAccount(AccountRepository accounts, PatientProfileRepository profiles,
            PasswordEncoder encoder, Environment environment) {
        return args -> {
            AccountJpaEntity patient = seed(accounts, encoder,
                    environment.getProperty("app.demo.email", "demo.patient@localhost"),
                    required(environment, "app.demo.password"), AccountType.PATIENT);
            ensurePatientProfile(profiles, patient, environment.getProperty("app.demo.full-name", "Nguyễn Văn An"));
            seed(accounts, encoder, environment.getProperty("app.demo.staff-email", "demo.staff@localhost"),
                    required(environment, "app.demo.staff-password"), AccountType.RECEPTIONIST);
        };
    }

    private static String required(Environment environment, String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException(key + " is required for demo profile");
        if (value.length() < 8) throw new IllegalStateException(key + " must contain at least 8 characters");
        return value;
    }

    /** Every patient account has a profile with the real full name (registration requires it), also when already seeded. */
    private static void ensurePatientProfile(PatientProfileRepository profiles, AccountJpaEntity account, String fullName) {
        if (profiles.findByAccountId(account.getId()).isPresent()) return;
        PatientProfileJpaEntity profile = new PatientProfileJpaEntity();
        profile.setAccount(account);
        profile.setAccountId(account.getId());
        profile.setFullName(fullName);
        profile.setPhoneCanonical("+84900000000");
        profile.setPhoneOwnerType(PhoneOwnerType.PATIENT);
        profile.setDateOfBirth(LocalDate.of(1990, 1, 1));
        profile.setGender(Gender.MALE);
        profiles.save(profile);
    }

    static AccountJpaEntity seed(AccountRepository accounts, PasswordEncoder encoder, String rawEmail, String password, AccountType type) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        var existing = accounts.findByNormalizedEmail(email);
        if (existing.isPresent()) return existing.get();
        Instant now = Instant.now();
        AccountJpaEntity account = new AccountJpaEntity();
        account.setNormalizedEmail(email);
        account.setAccountType(type);
        account.setStatus(AccountStatus.ACTIVE);
        account.setPasswordHash(encoder.encode(password));
        account.setEmailVerifiedAt(now);
        account.setFailedCredentialCount(0);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        return accounts.save(account);
    }
}
