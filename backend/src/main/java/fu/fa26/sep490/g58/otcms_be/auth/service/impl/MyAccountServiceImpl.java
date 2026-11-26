package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.exception.MyAccountOperationException;
import static fu.fa26.sep490.g58.otcms_be.auth.exception.MyAccountOperationException.Reason.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.MyAccountDtos.Avatar;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.storage.ObjectStoragePort;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class MyAccountServiceImpl implements MyAccountService {
    private static final Logger log = LoggerFactory.getLogger(MyAccountServiceImpl.class);
    private final AccountRepository accounts;
    private final MyAccountQueryService query;
    private final ObjectStoragePort storage;
    public MyAccountServiceImpl(AccountRepository accounts, MyAccountQueryService query, ObjectStoragePort storage) {
        this.accounts = accounts; this.query = query; this.storage = storage;
    }

    @Override @Transactional(readOnly = true)
    public MyAccountQueryService.MyAccountView get(AuthenticatedAccount actor) {
        require(actor, false, false);
        return query.find(actor.accountId()).orElseThrow(() -> new MyAccountOperationException(ACCESS_DENIED));
    }

    @Override @Transactional
    public MyAccountQueryService.MyAccountView updateDisplayName(AuthenticatedAccount actor, String requested) {
        var account = require(actor, true, true);
        if (account.getAccountType() == AccountType.DOCTOR)
            throw new MyAccountOperationException(ACCESS_DENIED);
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
        String previousKey = require(actor, true, true).getAvatarStorageKey();
        if (!validImage(content, type)) throw new MyAccountOperationException(INVALID_INPUT);
        String key = avatarKey(actor.accountId(), type);
        storage.put(key, type, content);
        try {
            accounts.updateAvatar(actor.accountId(), key, type, Instant.now());
        } catch (RuntimeException e) {
            deleteQuietly(key);
            throw e;
        }
        deleteAfterCommit(previousKey);
        return get(actor);
    }

    @Override @Transactional
    public void removeAvatar(AuthenticatedAccount actor) {
        String previousKey = require(actor, true, true).getAvatarStorageKey();
        accounts.clearAvatar(actor.accountId());
        deleteAfterCommit(previousKey);
    }

    @Override @Transactional(readOnly = true)
    public Avatar avatar(AuthenticatedAccount actor, UUID accountId) {
        require(actor, false, true);
        var owner = accounts.findById(accountId).filter(a -> a.getAvatarContentType() != null)
                .orElseThrow(() -> new MyAccountOperationException(AVATAR_NOT_FOUND));
        byte[] content = accounts.findAvatarStorageKey(accountId).flatMap(storage::get)
                .orElseThrow(() -> new MyAccountOperationException(AVATAR_NOT_FOUND));
        return new Avatar(content, owner.getAvatarContentType());
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

    private static String avatarKey(UUID accountId, String type) {
        String extension = switch (type) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("Unsupported avatar type");
        };
        return "avatars/" + accountId + "/" + UUID.randomUUID() + "." + extension;
    }

    /** Deletes the replaced object only after the change commits, so a rollback keeps the old avatar readable. */
    private void deleteAfterCommit(String key) {
        if (key == null) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { deleteQuietly(key); }
        });
    }

    /** Best-effort object cleanup: a failed delete is logged and never fails the request. */
    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Avatar object {} could not be deleted", key, e);
        }
    }
}
