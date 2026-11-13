package fu.fa26.sep490.g58.otcms_be.content;
import fu.fa26.sep490.g58.otcms_be.content.repository.*;
import fu.fa26.sep490.g58.otcms_be.content.exception.*;
import fu.fa26.sep490.g58.otcms_be.content.service.impl.*;
import fu.fa26.sep490.g58.otcms_be.content.service.*;
import fu.fa26.sep490.g58.otcms_be.content.model.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher;
import fu.fa26.sep490.g58.otcms_be.content.repository.ClinicContentRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ClinicContentServiceImplTest {
    private final ClinicContentRepository store=mock(ClinicContentRepository.class);
    private final NotificationPublisher publisher = mock(NotificationPublisher.class);
    private final ClinicContentServiceImpl handler=new ClinicContentServiceImpl(store,publisher);
    private final UUID actor=UUID.randomUUID();
    @Test void publishesTextWithSafeAuditAndPreservesOriginalPublicationOnUpdate() {
        when(store.insert(any(WebsiteContent.class))).thenAnswer(c -> c.getArgument(0));
        var entry=handler.create(new WebsiteContentInput("HEALTH_ARTICLE"," Health ",new MarkdownBody("# Plain text <script>not executed</script>"),null),actor);
        assertEquals("Health",entry.title());
        assertEquals("PUBLISHED",entry.status());
        verify(store).audit(eq(actor),eq("CONTENT"),eq("UPDATE_CONTENT"),eq(entry.id()),eq("Health"),any());
        when(store.content(entry.id(),false)).thenReturn(Optional.of(entry));
        when(store.update(any(),eq(0L))).thenAnswer(c -> c.getArgument(0));
        var next=handler.update(entry.id(),new WebsiteContentInput(null,"Updated",new MarkdownBody("New content"),null),actor);
        assertEquals(entry.publishedAt(),next.publishedAt());
        assertEquals(entry.createdBy(),next.createdBy());
        assertEquals(1L,next.version());
    }
    @Test void rejectsAnnouncementTypeIncludingWhitespaceOnCreateAndFilter() {
        for (String type : List.of("ANNOUNCEMENT", " ANNOUNCEMENT ")) {
            var createError = assertThrows(ContentOperationException.class,
                () -> handler.create(new WebsiteContentInput(type, "Notice", new MarkdownBody("Body"), null), actor));
            var filterError = assertThrows(ContentOperationException.class, () -> handler.list(type, "", 0, 20));
            assertEquals(ContentOperationException.Reason.INVALID_CONTENT, createError.reason());
            assertEquals(ContentOperationException.Reason.INVALID_CONTENT, filterError.reason());
        }
        verifyNoInteractions(store);
    }
    @Test void updateIgnoresAnnouncementTypeAsItIgnoresUnknownType() {
        when(store.insert(any(WebsiteContent.class))).thenAnswer(c -> c.getArgument(0));
        var entry = handler.create(new WebsiteContentInput("HEALTH_ARTICLE", "Title", new MarkdownBody("Body"), null), actor);
        when(store.content(entry.id(), false)).thenReturn(Optional.of(entry));
        when(store.update(any(), eq(0L))).thenAnswer(c -> c.getArgument(0));
        var unknown = handler.update(entry.id(), new WebsiteContentInput("UNKNOWN", "Updated", new MarkdownBody("Body"), null), actor);
        var announcement = handler.update(entry.id(), new WebsiteContentInput("ANNOUNCEMENT", "Updated", new MarkdownBody("Body"), null), actor);
        assertEquals(ContentType.HEALTH_ARTICLE, unknown.type());
        assertEquals(unknown.type(), announcement.type());
    }
    @Test void publicResponseSchemasExcludeInternalAnnouncementType() {
        var converters = io.swagger.v3.core.converter.ModelConverters.getInstance();
        for (Class<?> response : List.of(
                fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.WebsiteSummaryResponse.class,
                fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.WebsiteDetailResponse.class,
                fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.ArticleContentResponse.class)) {
            var schema = converters.read(response).get(response.getSimpleName());
            assertEquals(List.of("ABOUT_PAGE", "HEALTH_ARTICLE", "CLINIC_INFO"), ((io.swagger.v3.oas.models.media.Schema<?>) schema.getProperties().get("type")).getEnum());
        }
    }
    @Test void validatesContactFieldsAndImageSizeMimeAndSignatureBeforePersisting() {
        assertThrows(ContentOperationException.class,() -> handler.create(new WebsiteContentInput("CLINIC_INFO","Clinic",
        new ClinicContact("Name","Address","0900000000","bad",List.of("8-17")),null),actor));
        when(store.insert(any(WebsiteContent.class))).thenAnswer(c -> c.getArgument(0));
        var valid=handler.create(new WebsiteContentInput("CLINIC_INFO","Clinic",
        new ClinicContact("Name","Address","0900000000","info@clinic.vn",List.of("8-17")),null),actor);
        assertEquals("info@clinic.vn",((ClinicContact)valid.body()).email());
        byte[] png={
            (byte)137,80,78,71,13,10,26,10,0,0,0,0
        };
        handler.upload(png,"image/png");
        verify(store).image(any(WebsiteImage.class));
        clearInvocations(store);
        assertThrows(ContentOperationException.class,() -> handler.upload(png,"image/jpeg"));
        assertThrows(ContentOperationException.class,() -> handler.upload(new byte[2097153],"image/png"));
        assertThrows(ContentOperationException.class,() -> handler.upload("<html>not an image</html>".getBytes(),"image/png"));
        verifyNoInteractions(store);
    }
    @Test void deliversPrivateAnnouncementsAndOnlyAcceptsSupportedChannels() {
        assertEquals(List.of("PUBLIC", "PATIENT", "ORTHOPEDIC_DOCTOR", "RECEPTIONIST", "IMAGING_TECHNICIAN", "LAB_TECHNICIAN"), handler.options().audiences());
        when(store.insert(any(ClinicAnnouncement.class))).thenAnswer(call -> call.getArgument(0));
        handler.announce(new AnnouncementInput("Title", "Body", "PATIENT", List.of("IN_APP")), actor);
        verify(publisher).publishToRole(eq(fu.fa26.sep490.g58.otcms_be.common.security.AccountRole.PATIENT), any());
        clearInvocations(store);
        assertThrows(ContentOperationException.class, () -> handler.announce(new AnnouncementInput("Title", "Body", "PUBLIC", List.of("EMAIL")), actor));
        verifyNoInteractions(store);
    }
    @Test void publicAnnouncementIsPersistedAndAuditedWithoutPrivateDelivery() {
        when(store.insert(any(ClinicAnnouncement.class))).thenAnswer(c -> c.getArgument(0));
        var entry=handler.announce(new AnnouncementInput("Closure","Clinic closed Monday","PUBLIC",List.of("IN_APP")),actor);
        assertEquals("PUBLIC",entry.audience());
        assertNull(entry.recipientRole());
        verify(store).audit(eq(actor),eq("ANNOUNCEMENT"),eq("CREATE_ANNOUNCEMENT"),eq(entry.id()),eq(entry.title()),any());
    }
    @Test void queriesPublicScopeAndRejectsReversedDatesAndInvalidPagination() {
        when(store.announcement(any(),eq(true))).thenReturn(Optional.empty());
        UUID id=UUID.randomUUID();
        assertEquals("MSG-50",assertThrows(ContentOperationException.class,() -> handler.announcement(id,true)).getMessage());
        verify(store).announcement(id,true);
        assertThrows(ContentOperationException.class,() -> handler.announcements("","","2026-11-07","2026-11-01",0,20,false));
        assertThrows(ContentOperationException.class,() -> handler.list("","",-1,20));
    }
}
