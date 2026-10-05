package fu.fa26.sep490.g58.otcms_be.config;

import fu.fa26.sep490.g58.otcms_be.auth.entity.Account;
import fu.fa26.sep490.g58.otcms_be.auth.entity.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.entity.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.core.env.Environment;

import java.time.Instant;
import java.util.Locale;

/** Local/demo-only seed. It is not loaded unless the {@code demo} profile is active. */
@Configuration
@Profile("demo")
public class DemoAccountSeeder {
    @Bean
    public CommandLineRunner seedDemoAccount(AccountRepository accounts, PasswordEncoder encoder, Environment environment) {
        return args -> {
            seed(accounts, encoder, environment.getProperty("app.demo.email", "demo.patient@localhost"),
                    required(environment, "app.demo.password"), AccountType.PATIENT);
            seed(accounts, encoder, environment.getProperty("app.demo.staff-email", "demo.staff@localhost"),
                    required(environment, "app.demo.staff-password"), AccountType.STAFF);
        };
    }

    private static String required(Environment environment, String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException(key + " is required for demo profile");
        if (value.length() < 8) throw new IllegalStateException(key + " must contain at least 8 characters");
        return value;
    }

    private static void seed(AccountRepository accounts, PasswordEncoder encoder, String rawEmail, String password, AccountType type) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (accounts.existsByNormalizedEmail(email)) return;
        Instant now = Instant.now();
        Account account = new Account();
        account.setNormalizedEmail(email);
        account.setAccountType(type);
        account.setStatus(AccountStatus.ACTIVE);
        account.setPasswordHash(encoder.encode(password));
        account.setEmailVerifiedAt(now);
        account.setFailedCredentialCount(0);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        accounts.save(account);
    }
}
