package fu.fa26.sep490.g58.otcms_be.auth.config;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.validation.AdministratorUsernamePolicy;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** One-shot local bootstrap. This profile must never be used with a shared or production database. */
@Component
@Profile("local-admin-bootstrap")
public class LocalAdministratorBootstrap implements CommandLineRunner {
    private static final String ALLOWED_DATASOURCE =
            "jdbc:postgresql://127.0.0.1:5432/otcms_auth_local";

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final String datasourceUrl;
    private final String username;
    private final String password;

    public LocalAdministratorBootstrap(AccountRepository accounts, PasswordEncoder passwordEncoder,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${app.local-admin.username:}") String username,
            @Value("${app.local-admin.password:}") String password) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.datasourceUrl = datasourceUrl;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (!ALLOWED_DATASOURCE.equals(datasourceUrl)) {
            throw new IllegalStateException("Local admin bootstrap only allows otcms_auth_local on 127.0.0.1:5432");
        }
        if (!AdministratorUsernamePolicy.isValid(username)) {
            throw new IllegalArgumentException("Administrator username is blank, invalid, or longer than 64 characters");
        }
        if (password == null || password.length() < 12) {
            throw new IllegalArgumentException("Administrator password must contain at least 12 characters");
        }
        if (accounts.existsByUsername(username)) {
            throw new IllegalStateException("That username already exists; no account was created");
        }

        Instant now = Instant.now();
        AccountJpaEntity administrator = new AccountJpaEntity();
        administrator.setUsername(username);
        administrator.setAccountType(AccountType.ADMIN);
        administrator.setStatus(AccountStatus.ACTIVE);
        administrator.setPasswordHash(passwordEncoder.encode(password));
        administrator.setMustChangePassword(true);
        administrator.setFailedCredentialCount(0);
        administrator.setCreatedAt(now);
        administrator.setUpdatedAt(now);
        accounts.saveAndFlush(administrator);

        System.out.println("Local ADMIN account created. Sign in and change its password immediately.");
    }
}
