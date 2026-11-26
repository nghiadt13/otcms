package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.AccountAuthorDirectory;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountAuthorDirectoryImpl implements AccountAuthorDirectory {
    private final AccountRepository accounts;

    public AccountAuthorDirectoryImpl(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> names(Collection<UUID> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) return Map.of();
        return accounts.findAuthorNames(accountIds).stream().collect(Collectors.toMap(
                AccountRepository.AuthorName::getId, AccountRepository.AuthorName::getAuthor, (first, ignored) -> first));
    }
}
