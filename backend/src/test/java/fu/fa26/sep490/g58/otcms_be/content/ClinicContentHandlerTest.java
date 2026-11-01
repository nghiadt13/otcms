package fu.fa26.sep490.g58.otcms_be.content;
import fu.fa26.sep490.g58.otcms_be.content.application.*;
import fu.fa26.sep490.g58.otcms_be.content.domain.ContentModels.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ClinicContentHandlerTest {
    private final ContentStore store=mock(ContentStore.class);
    @SuppressWarnings("unchecked") private final ObjectProvider<AnnouncementRecipientPublisher> publishers=mock(ObjectProvider.class);
    private final ClinicContentHandler handler=new ClinicContentHandler(store,publishers);
    private final UUID actor=UUID.randomUUID();
    @Test void publishesTextWithSafeAuditAndPreservesOriginalPublicationOnUpdate() {
        when(store.insert(any(WebsiteEntry.class))).thenAnswer(c -> c.getArgument(0));
        var entry=handler.create(new ManageClinicContent.Input("HEALTH_ARTICLE"," Health ","# Plain text <script>not executed</script>",null),actor);
        assertEquals("Health",entry.title()); assertEquals("PUBLISHED",entry.status());
        verify(store).audit(eq(actor),eq("CONTENT"),eq("UPDATE_CONTENT"),eq(entry.id()),eq("Health"),any());
        when(store.content(entry.id(),false)).thenReturn(Optional.of(entry));
        when(store.update(any(),eq(0L))).thenAnswer(c -> c.getArgument(0));
        var next=handler.update(entry.id(),new ManageClinicContent.Input(null,"Updated","New content",null),actor);
        assertEquals(entry.publishedAt(),next.publishedAt()); assertEquals(entry.createdBy(),next.createdBy());
        assertEquals(1L,next.version());
    }
    @Test void validatesContactFieldsAndImageSizeMimeAndSignatureBeforePersisting() {
        assertThrows(ContentFailure.class,() -> handler.create(new ManageClinicContent.Input("CLINIC_INFO","Clinic",
                new Contact("Name","Address","0900000000","bad",List.of("8-17")),null),actor));
        when(store.insert(any(WebsiteEntry.class))).thenAnswer(c -> c.getArgument(0));
        var valid=handler.create(new ManageClinicContent.Input("CLINIC_INFO","Clinic",
                new Contact("Name","Address","0900000000","info@clinic.vn",List.of("8-17")),null),actor);
        assertEquals("info@clinic.vn",((Contact)valid.body()).email());
        byte[] png={(byte)137,80,78,71,13,10,26,10,0,0,0,0};
        handler.upload(png,"image/png"); verify(store).image(any(Image.class));
        clearInvocations(store);
        assertThrows(ContentFailure.class,() -> handler.upload(png,"image/jpeg"));
        assertThrows(ContentFailure.class,() -> handler.upload(new byte[2097153],"image/png"));
        assertThrows(ContentFailure.class,() -> handler.upload("<html>not an image</html>".getBytes(),"image/png"));
        verifyNoInteractions(store);
    }
    @Test void rejectsUnimplementedDeliveryBeforeWritingAndOnlyAdvertisesPublicRecipient() {
        assertEquals(List.of("PUBLIC"),handler.options().audiences());
        var ex=assertThrows(ContentFailure.class,() -> handler.announce(new ManageClinicContent.AnnouncementInput("Title","Body","PATIENT",List.of("IN_APP")),actor));
        assertEquals(503,ex.status()); assertEquals("MSG-04",ex.getMessage()); verifyNoInteractions(store);
        assertThrows(ContentFailure.class,() -> handler.announce(new ManageClinicContent.AnnouncementInput("Title","Body","PUBLIC",List.of("EMAIL")),actor));
        verifyNoInteractions(store);
    }
    @Test void publicAnnouncementIsPersistedAndAuditedWithoutPrivateDelivery() {
        when(store.insert(any(Announcement.class))).thenAnswer(c -> c.getArgument(0));
        var entry=handler.announce(new ManageClinicContent.AnnouncementInput("Closure","Clinic closed Monday","PUBLIC",List.of("IN_APP")),actor);
        assertEquals("PUBLIC",entry.audience()); assertNull(entry.recipientRole());
        verify(store).audit(eq(actor),eq("ANNOUNCEMENT"),eq("CREATE_ANNOUNCEMENT"),eq(entry.id()),eq(entry.title()),any());
    }
    @Test void queriesPublicScopeAndRejectsReversedDatesAndInvalidPagination() {
        when(store.announcement(any(),eq(true))).thenReturn(Optional.empty());
        UUID id=UUID.randomUUID();
        assertEquals("MSG-50",assertThrows(ContentFailure.class,() -> handler.announcement(id,true)).getMessage());
        verify(store).announcement(id,true);
        assertThrows(ContentFailure.class,() -> handler.announcements("","","2026-11-07","2026-11-01",0,20,false));
        assertThrows(ContentFailure.class,() -> handler.list("","",-1,20));
    }
}
