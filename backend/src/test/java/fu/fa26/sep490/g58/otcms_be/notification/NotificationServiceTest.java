package fu.fa26.sep490.g58.otcms_be.notification;

import fu.fa26.sep490.g58.otcms_be.auth.service.NotificationRecipientDirectory;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.notification.model.*;
import fu.fa26.sep490.g58.otcms_be.notification.model.entity.NotificationJpaEntity;
import fu.fa26.sep490.g58.otcms_be.notification.repository.NotificationRepository;
import fu.fa26.sep490.g58.otcms_be.notification.service.*;
import fu.fa26.sep490.g58.otcms_be.notification.service.impl.*;
import fu.fa26.sep490.g58.otcms_be.notification.exception.NotificationNotFoundException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class NotificationServiceTest {
    final NotificationRepository repo = mock(NotificationRepository.class);
    final NotificationRelatedRecordResolver resolver = mock(NotificationRelatedRecordResolver.class);
    final Instant now = Instant.parse("2026-11-01T08:00:00Z");
    final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    final AuthenticatedAccount actor = new AuthenticatedAccount(UUID.randomUUID(), AccountRole.PATIENT, UUID.randomUUID());
    final MyNotificationServiceImpl service = new MyNotificationServiceImpl(repo, List.of(resolver), clock);
    NotificationJpaEntity notification() {
        return NotificationJpaEntity.create(actor.accountId(), new NotificationMessage("TEST", "Title", "Content",
                NotificationRelatedKind.VISIT, UUID.randomUUID()), now.minusSeconds(30));
    }
    @Test void listUsesOnlyPrincipalAndNewestSort() {
        var n = notification();
        when(repo.findByRecipientAccountId(eq(actor.accountId()), any())).thenAnswer(invocation ->
                new PageImpl<>(List.of(n), invocation.getArgument(1), 1));
        when(resolver.kind()).thenReturn(NotificationRelatedKind.VISIT);
        when(resolver.available(actor, n.getRelatedId())).thenReturn(true);
        var page = service.list(actor, false, 0, 10);
        assertEquals(List.of("markRead"), page.items().getFirst().allowedActions());
        assertTrue(page.items().getFirst().relatedRecord().available());
        var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findByRecipientAccountId(eq(actor.accountId()), captor.capture());
        assertEquals(Sort.Direction.DESC, captor.getValue().getSort().getOrderFor("createdAt").getDirection());
        when(resolver.available(actor, n.getRelatedId())).thenReturn(false);
        assertFalse(service.list(actor, false, 0, 10).items().getFirst().relatedRecord().available());
    }
    @Test void unreadQueryAndCountAreScoped() {
        when(repo.findByRecipientAccountIdAndReadAtIsNull(eq(actor.accountId()), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(repo.countByRecipientAccountIdAndReadAtIsNull(actor.accountId())).thenReturn(3L);
        assertTrue(service.list(actor, true, 0, 20).items().isEmpty());
        assertEquals(3, service.unreadCount(actor).unreadCount());
        verify(repo, never()).findByRecipientAccountId(any(), any());
    }
    @Test void markReadIsIdempotentAndDoesNotWriteAgain() {
        var n = notification(); when(repo.findOwnedForUpdate(n.getId(), actor.accountId())).thenReturn(Optional.of(n));
        var first = service.markRead(actor, n.getId()); var timestamp = n.getReadAt();
        assertTrue(first.read()); assertTrue(first.allowedActions().isEmpty());
        assertEquals(first, service.markRead(actor, n.getId())); assertEquals(timestamp, n.getReadAt());
        verify(repo, times(1)).saveAndFlush(n);
    }
    @Test void foreignAndMissingAreIndistinguishable() {
        UUID id = UUID.randomUUID(); when(repo.findOwnedForUpdate(id, actor.accountId())).thenReturn(Optional.empty());
        assertThrows(NotificationNotFoundException.class, () -> service.markRead(actor, id));
        verify(repo, never()).saveAndFlush(any());
    }
    @Test void readAllUsesCurrentAccountAndReturnsUpdatedCount() {
        when(repo.markAllRead(actor.accountId(), now)).thenReturn(2);
        assertEquals(2, service.readAll(actor).updatedCount());
    }
    @Test void publisherSkipsUnlinkedPatientAndTargetsExactRole() {
        var recipients = mock(NotificationRecipientDirectory.class);
        var publisher = new NotificationPublisherImpl(repo, recipients, clock);
        var message = new NotificationMessage("TEST", "Title", "Content", NotificationRelatedKind.REVIEW, UUID.randomUUID());
        UUID profile = UUID.randomUUID(); when(recipients.patientAccount(profile)).thenReturn(Optional.empty());
        publisher.publishToPatient(profile, message); verifyNoInteractions(repo);
        when(recipients.activeAccounts(AccountRole.ADMIN)).thenReturn(List.of(actor.accountId()));
        publisher.publishToRole(AccountRole.ADMIN, message);
        var captured = org.mockito.ArgumentCaptor.forClass(NotificationJpaEntity.class);
        verify(repo).saveAndFlush(captured.capture()); assertEquals(actor.accountId(), captured.getValue().getRecipientAccountId());
        assertEquals(NotificationRelatedKind.REVIEW, captured.getValue().getRelatedKind());
        verify(recipients, never()).activeAccounts(AccountRole.OWNER);
    }
    @Test void existingErrorResponseJsonRemainsBackwardCompatible() {
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        var legacy = ErrorResponse.of("MSG-04", "Safe message", "correlation");
        var json = mapper.valueToTree(legacy);
        assertEquals(1, json.size()); assertEquals(5, json.get("error").size());
        for (String field : List.of("code", "message", "timestamp", "correlationId", "impact")) assertTrue(json.get("error").has(field));
        assertFalse(json.has("code")); assertFalse(json.has("fieldErrors"));
        assertEquals(json, mapper.valueToTree(new ErrorResponse(legacy.error())));
        var validation = mapper.valueToTree(ErrorResponse.withFieldErrors("MSG-57", "Invalid", "correlation",
                List.of(new ErrorResponse.FieldError("text", "MSG-57"))));
        assertEquals("MSG-57", validation.get("error").get("code").asText()); assertTrue(validation.has("fieldErrors"));
    }
}
