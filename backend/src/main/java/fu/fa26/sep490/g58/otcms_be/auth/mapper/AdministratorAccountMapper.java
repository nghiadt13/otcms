package fu.fa26.sep490.g58.otcms_be.auth.mapper;

import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.AdministratorAccountResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class AdministratorAccountMapper {
    public AdministratorAccountResponse toResponse(AccountJpaEntity account) {
        return new AdministratorAccountResponse(account.getId(), account.getUsername(), account.getNormalizedEmail(),
                account.getAccountType().name(), account.getStatus().name(), account.getCreatedAt(), account.getUpdatedAt());
    }
}
