package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.auth.exception.MyAccountOperationException;
import fu.fa26.sep490.g58.otcms_be.auth.exception.MyAccountOperationException.Reason;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.MyAccountQueryService;
import fu.fa26.sep490.g58.otcms_be.auth.service.MyAccountQueryService.MyAccountView;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.storage.ObjectStoragePort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MyAccountServiceImplTest {
    private static final byte[] PNG = {(byte) 137, 'P', 'N', 'G', 13, 10, 26, 10};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final MyAccountQueryService query = mock(MyAccountQueryService.class);
    private final ObjectStoragePort storage = mock(ObjectStoragePort.class);
    private final MyAccountServiceImpl service = new MyAccountServiceImpl(accounts, query, storage);

    private final UUID accountId = UUID.randomUUID();
    private final AuthenticatedAccount patientActor =
            new AuthenticatedAccount(accountId, AccountRole.PATIENT, UUID.randomUUID());

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private AccountJpaEntity account(AccountType type, AccountStatus status, boolean mustChangePassword) {
        AccountJpaEntity entity = new AccountJpaEntity();
        entity.setId(accountId);
        entity.setAccountType(type);
        entity.setStatus(status);
        entity.setMustChangePassword(mustChangePassword);
        entity.setNormalizedEmail("user@example.test");
        return entity;
    }

    private MyAccountView sampleView(String name) {
        return new MyAccountView(accountId, "PATIENT", "user@example.test", name);
    }

    @Test
    void getReturnsViewForActiveMatchingActorAndRejectsInvalidStates() {
        AccountJpaEntity forcedChange = account(AccountType.PATIENT, AccountStatus.ACTIVE, true);
        MyAccountView view = sampleView("An");
        when(accounts.findById(accountId)).thenReturn(Optional.of(forcedChange));
        when(query.find(accountId)).thenReturn(Optional.of(view));

        assertSame(view, service.get(patientActor));

        assertEquals(Reason.ACCESS_DENIED, assertThrows(MyAccountOperationException.class,
                () -> service.get(null)).reason());
        assertEquals(Reason.ACCESS_DENIED, assertThrows(MyAccountOperationException.class,
                () -> service.get(new AuthenticatedAccount(null, AccountRole.PATIENT, UUID.randomUUID()))).reason());
        assertEquals(Reason.ACCESS_DENIED, assertThrows(MyAccountOperationException.class,
                () -> service.get(new AuthenticatedAccount(accountId, null, UUID.randomUUID()))).reason());

        when(accounts.findById(accountId)).thenReturn(Optional.empty());
        assertEquals(Reason.ACCESS_DENIED, assertThrows(MyAccountOperationException.class,
                () -> service.get(patientActor)).reason());

        when(accounts.findById(accountId)).thenReturn(Optional.of(account(AccountType.DOCTOR, AccountStatus.ACTIVE, false)));
        assertEquals(Reason.ACCESS_DENIED, assertThrows(MyAccountOperationException.class,
                () -> service.get(patientActor)).reason());

        when(accounts.findById(accountId)).thenReturn(Optional.of(account(AccountType.PATIENT, AccountStatus.DISABLED, false)));
        assertEquals(Reason.ACCESS_DENIED, assertThrows(MyAccountOperationException.class,
                () -> service.get(patientActor)).reason());
    }

    @Test
    void updateDisplayNameTrimsPersistsFlushesAndReturnsUpdatedView() {
        AccountJpaEntity entity = account(AccountType.PATIENT, AccountStatus.ACTIVE, false);
        MyAccountView updatedView = sampleView("An");
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(entity));
        when(accounts.findById(accountId)).thenReturn(Optional.of(entity));
        when(query.find(accountId)).thenReturn(Optional.of(updatedView));

        assertSame(updatedView, service.updateDisplayName(patientActor, "  An  "));
        assertEquals("An", entity.getDisplayName());

        String hundredEmoji = "\uD83D\uDE00".repeat(100);
        service.updateDisplayName(patientActor, hundredEmoji);
        assertEquals(hundredEmoji, entity.getDisplayName());
        verify(accounts, org.mockito.Mockito.times(2)).flush();
    }

    @Test
    void updateDisplayNameRejectsInvalidNamesAndMustChangePassword() {
        AccountJpaEntity active = account(AccountType.PATIENT, AccountStatus.ACTIVE, false);
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(active));

        for (String bad : new String[]{null, "   ", "X", "x".repeat(101), "\uD83D\uDE00".repeat(101)}) {
            assertEquals(Reason.INVALID_INPUT, assertThrows(MyAccountOperationException.class,
                    () -> service.updateDisplayName(patientActor, bad)).reason());
        }

        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(account(AccountType.PATIENT, AccountStatus.ACTIVE, true)));
        assertEquals(Reason.ACCESS_DENIED, assertThrows(MyAccountOperationException.class,
                () -> service.updateDisplayName(patientActor, "Valid Name")).reason());
        verify(accounts, never()).flush();
    }

    @Test
    void updateAvatarStoresValidJpegPngWebpAndDeletesPreviousKeyAfterCommit() {
        AccountJpaEntity entity = account(AccountType.PATIENT, AccountStatus.ACTIVE, false);
        entity.setAvatarStorageKey("avatars/" + accountId + "/old.png");
        MyAccountView view = sampleView("An");
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(entity));
        when(accounts.findById(accountId)).thenReturn(Optional.of(entity));
        when(query.find(accountId)).thenReturn(Optional.of(view));

        TransactionSynchronizationManager.initSynchronization();
        assertSame(view, service.updateAvatar(patientActor, PNG, "image/png"));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        var order = inOrder(storage, accounts);
        order.verify(storage).put(keyCaptor.capture(), eq("image/png"), eq(PNG));
        order.verify(accounts).updateAvatar(eq(accountId), eq(keyCaptor.getValue()), eq("image/png"), any(Instant.class));
        assertEquals("avatars/" + accountId + "/", keyCaptor.getValue().substring(0, ("avatars/" + accountId + "/").length()));
        verify(storage, never()).delete(any());

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        verify(storage).delete("avatars/" + accountId + "/old.png");
        TransactionSynchronizationManager.clearSynchronization();

        // Also accepts JPEG, WEBP, and exact 2 MiB boundary when synchronization is inactive (fallback immediate cleanup).
        byte[] maxJpeg = new byte[2 * 1024 * 1024];
        System.arraycopy(JPEG, 0, maxJpeg, 0, JPEG.length);
        service.updateAvatar(patientActor, maxJpeg, "image/jpeg");
        service.updateAvatar(patientActor, WEBP, "image/webp");
        verify(storage).put(argThat(k -> k.endsWith(".jpg")), eq("image/jpeg"), eq(maxJpeg));
        verify(storage).put(argThat(k -> k.endsWith(".webp")), eq("image/webp"), eq(WEBP));
    }

    @Test
    void updateAvatarRejectsInvalidPayloadOrContentTypeWithoutUploading() {
        when(accounts.findByIdForUpdate(accountId))
                .thenReturn(Optional.of(account(AccountType.PATIENT, AccountStatus.ACTIVE, false)));
        byte[] oversized = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(JPEG, 0, oversized, 0, JPEG.length);

        assertEquals(Reason.INVALID_INPUT, assertThrows(MyAccountOperationException.class,
                () -> service.updateAvatar(patientActor, null, "image/png")).reason());
        assertEquals(Reason.INVALID_INPUT, assertThrows(MyAccountOperationException.class,
                () -> service.updateAvatar(patientActor, new byte[0], "image/png")).reason());
        assertEquals(Reason.INVALID_INPUT, assertThrows(MyAccountOperationException.class,
                () -> service.updateAvatar(patientActor, oversized, "image/jpeg")).reason());
        assertEquals(Reason.INVALID_INPUT, assertThrows(MyAccountOperationException.class,
                () -> service.updateAvatar(patientActor, PNG, null)).reason());
        assertEquals(Reason.INVALID_INPUT, assertThrows(MyAccountOperationException.class,
                () -> service.updateAvatar(patientActor, PNG, "image/gif")).reason());
        assertEquals(Reason.INVALID_INPUT, assertThrows(MyAccountOperationException.class,
                () -> service.updateAvatar(patientActor, PNG, "image/jpeg")).reason());
        assertEquals(Reason.INVALID_INPUT, assertThrows(MyAccountOperationException.class,
                () -> service.updateAvatar(patientActor, new byte[]{(byte) 0xFF, (byte) 0xD8}, "image/jpeg")).reason());
        assertEquals(Reason.INVALID_INPUT, assertThrows(MyAccountOperationException.class,
                () -> service.updateAvatar(patientActor, new byte[]{(byte) 137, 'P', 'N', 'G', 13, 10, 26}, "image/png")).reason());
        assertEquals(Reason.INVALID_INPUT, assertThrows(MyAccountOperationException.class,
                () -> service.updateAvatar(patientActor, new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B'}, "image/webp")).reason());
        verifyNoInteractions(storage);
    }

    @Test
    void updateAvatarDeletesNewlyUploadedObjectQuietlyWhenDbUpdateFails() {
        AccountJpaEntity entity = account(AccountType.PATIENT, AccountStatus.ACTIVE, false);
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(entity));
        doThrow(new IllegalStateException("db failed")).when(accounts)
                .updateAvatar(eq(accountId), any(), eq("image/png"), any(Instant.class));
        doThrow(new RuntimeException("delete failed")).when(storage).delete(any());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.updateAvatar(patientActor, PNG, "image/png"));

        assertEquals("db failed", failure.getMessage());
        verify(storage).delete(argThat(k -> k.startsWith("avatars/" + accountId + "/")));
    }

    @Test
    void removeAvatarClearsDbAndDeletesPreviousKeyAfterCommitOrNoOpsWhenNoPreviousKey() {
        AccountJpaEntity withAvatar = account(AccountType.PATIENT, AccountStatus.ACTIVE, false);
        withAvatar.setAvatarStorageKey("avatars/" + accountId + "/existing.png");
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(withAvatar));

        TransactionSynchronizationManager.initSynchronization();
        service.removeAvatar(patientActor);
        verify(accounts).clearAvatar(accountId);
        verify(storage, never()).delete(any());
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        verify(storage).delete("avatars/" + accountId + "/existing.png");
        TransactionSynchronizationManager.clearSynchronization();

        // Fallback when synchronization is inactive (does not throw IllegalStateException).
        service.removeAvatar(patientActor);
        verify(storage, org.mockito.Mockito.times(2)).delete("avatars/" + accountId + "/existing.png");

        // No previous key -> no storage interaction.
        AccountJpaEntity withoutAvatar = account(AccountType.PATIENT, AccountStatus.ACTIVE, false);
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(withoutAvatar));
        service.removeAvatar(patientActor);
        verify(storage, org.mockito.Mockito.times(2)).delete(any());
    }

    @Test
    void avatarAllowsOwnAndStaffReadsButHidesOtherPatientsAvatar() {
        AccountJpaEntity patientEntity = account(AccountType.PATIENT, AccountStatus.ACTIVE, false);
        patientEntity.setAvatarContentType("image/png");
        String ownKey = "avatars/" + accountId + "/own.png";
        when(accounts.findById(accountId)).thenReturn(Optional.of(patientEntity));
        when(accounts.findAvatarStorageKey(accountId)).thenReturn(Optional.of(ownKey));
        when(storage.get(ownKey)).thenReturn(Optional.of(PNG));

        var ownAvatar = service.avatar(patientActor, accountId);
        assertArrayEquals(PNG, ownAvatar.content());
        assertEquals("image/png", ownAvatar.contentType());

        UUID otherId = UUID.randomUUID();
        assertEquals(Reason.AVATAR_NOT_FOUND, assertThrows(MyAccountOperationException.class,
                () -> service.avatar(patientActor, otherId)).reason());
        verify(accounts, never()).findById(otherId);

        UUID staffId = UUID.randomUUID();
        AuthenticatedAccount adminActor = new AuthenticatedAccount(staffId, AccountRole.ADMIN, UUID.randomUUID());
        AccountJpaEntity adminEntity = account(AccountType.ADMIN, AccountStatus.ACTIVE, false);
        adminEntity.setId(staffId);
        when(accounts.findById(staffId)).thenReturn(Optional.of(adminEntity));
        when(accounts.findById(otherId)).thenReturn(Optional.of(patientEntity));
        when(accounts.findAvatarStorageKey(otherId)).thenReturn(Optional.of(ownKey));

        var staffRead = service.avatar(adminActor, otherId);
        assertArrayEquals(PNG, staffRead.content());

        AccountJpaEntity forcedChange = account(AccountType.PATIENT, AccountStatus.ACTIVE, true);
        when(accounts.findById(accountId)).thenReturn(Optional.of(forcedChange));
        assertEquals(Reason.ACCESS_DENIED, assertThrows(MyAccountOperationException.class,
                () -> service.avatar(patientActor, accountId)).reason());
    }

    @Test
    void avatarReturnsNotFoundWhenAccountMissingTypeNullStorageMissingOrIdNull() {
        UUID staffId = UUID.randomUUID();
        AuthenticatedAccount doctorActor = new AuthenticatedAccount(staffId, AccountRole.DOCTOR, UUID.randomUUID());
        AccountJpaEntity doctorEntity = account(AccountType.DOCTOR, AccountStatus.ACTIVE, false);
        doctorEntity.setId(staffId);
        when(accounts.findById(staffId)).thenReturn(Optional.of(doctorEntity));

        assertEquals(Reason.AVATAR_NOT_FOUND, assertThrows(MyAccountOperationException.class,
                () -> service.avatar(doctorActor, null)).reason());
        verify(accounts, never()).findById(null);

        UUID targetId = UUID.randomUUID();
        when(accounts.findById(targetId)).thenReturn(Optional.empty());
        assertEquals(Reason.AVATAR_NOT_FOUND, assertThrows(MyAccountOperationException.class,
                () -> service.avatar(doctorActor, targetId)).reason());

        AccountJpaEntity noType = account(AccountType.PATIENT, AccountStatus.ACTIVE, false);
        noType.setId(targetId);
        when(accounts.findById(targetId)).thenReturn(Optional.of(noType));
        assertEquals(Reason.AVATAR_NOT_FOUND, assertThrows(MyAccountOperationException.class,
                () -> service.avatar(doctorActor, targetId)).reason());

        noType.setAvatarContentType("image/png");
        when(accounts.findAvatarStorageKey(targetId)).thenReturn(Optional.empty());
        assertEquals(Reason.AVATAR_NOT_FOUND, assertThrows(MyAccountOperationException.class,
                () -> service.avatar(doctorActor, targetId)).reason());

        when(accounts.findAvatarStorageKey(targetId)).thenReturn(Optional.of("avatars/" + targetId + "/missing.png"));
        when(storage.get("avatars/" + targetId + "/missing.png")).thenReturn(Optional.empty());
        assertEquals(Reason.AVATAR_NOT_FOUND, assertThrows(MyAccountOperationException.class,
                () -> service.avatar(doctorActor, targetId)).reason());
    }
}
