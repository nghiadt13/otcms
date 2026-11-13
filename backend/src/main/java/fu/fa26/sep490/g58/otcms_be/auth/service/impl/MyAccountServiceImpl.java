package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.exception.MyAccountOperationException;
import static fu.fa26.sep490.g58.otcms_be.auth.exception.MyAccountOperationException.Reason.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.MyAccountDtos.Avatar;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MyAccountServiceImpl implements MyAccountService {
    private final AccountRepository accounts;
    private final MyAccountQueryService query;
    public MyAccountServiceImpl(AccountRepository accounts, MyAccountQueryService query) {
        this.accounts = accounts; this.query = query;
    }

    @Override @Transactional(readOnly = true)
    public MyAccountQueryService.MyAccountView get(AuthenticatedAccount actor) {
        require(actor, false, false);
        return query.find(actor.accountId()).orElseThrow(() -> new MyAccountOperationException(ACCESS_DENIED));
    }

    @Override @Transactional
    public MyAccountQueryService.MyAccountView updateDisplayName(AuthenticatedAccount actor, String requested) {
        var account = require(actor, true, true);
        String name = requested == null ? "" : requested.trim();
        int length = name.codePointCount(0, name.length());
        if (length < 2 || length > 100) throw new MyAccountOperationException(INVALID_INPUT);
        account.setDisplayName(name);
        account.setUpdatedAt(Instant.now());
        accounts.flush();
        return get(actor);
    }

    @Override @Transactional
    public MyAccountQueryService.MyAccountView updateAvatar(AuthenticatedAccount actor, byte[] content, String type) {
        require(actor, true, true);
        if (!validImage(content, type)) throw new MyAccountOperationException(INVALID_INPUT);
        accounts.updateAvatar(actor.accountId(), content.clone(), type, Instant.now());
        return get(actor);
    }

    @Override @Transactional
    public void removeAvatar(AuthenticatedAccount actor) {
        require(actor, true, true);
        accounts.clearAvatar(actor.accountId());
    }

    @Override @Transactional(readOnly = true)
    public Avatar avatar(AuthenticatedAccount actor, UUID accountId) {
        require(actor, false, true);
        var owner = accounts.findById(accountId).filter(a -> a.getAvatarContentType() != null)
                .orElseThrow(() -> new MyAccountOperationException(AVATAR_NOT_FOUND));
        byte[] content = accounts.findAvatarContent(accountId)
                .orElseThrow(() -> new MyAccountOperationException(AVATAR_NOT_FOUND));
        return new Avatar(content.clone(), owner.getAvatarContentType());
    }

    private AccountJpaEntity require(AuthenticatedAccount actor, boolean lock, boolean mutationGate) {
        if (actor == null || actor.accountId() == null || actor.accountRole() == null)
            throw new MyAccountOperationException(ACCESS_DENIED);
        var a = (lock ? accounts.findByIdForUpdate(actor.accountId()) : accounts.findById(actor.accountId()))
                .orElseThrow(() -> new MyAccountOperationException(ACCESS_DENIED));
        if (!a.getAccountType().name().equals(actor.accountRole().name()) || a.getStatus() != AccountStatus.ACTIVE
                || mutationGate && a.isMustChangePassword()) throw new MyAccountOperationException(ACCESS_DENIED);
        return a;
    }

    private boolean validImage(byte[] b, String type) {
        if (b == null || b.length == 0 || b.length > 2 * 1024 * 1024 || type == null) return false;
        return switch (type) {
            case "image/jpeg" -> b.length >= 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255;
            case "image/png" -> b.length >= 8 && b[0] == (byte) 137 && b[1] == 80 && b[2] == 78 && b[3] == 71
                    && b[4] == 13 && b[5] == 10 && b[6] == 26 && b[7] == 10;
            case "image/webp" -> b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            default -> false;
        };
    }
}
