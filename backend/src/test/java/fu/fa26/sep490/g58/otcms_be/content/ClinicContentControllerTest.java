package fu.fa26.sep490.g58.otcms_be.content;
import fu.fa26.sep490.g58.otcms_be.content.repository.*;
import fu.fa26.sep490.g58.otcms_be.content.exception.*;
import fu.fa26.sep490.g58.otcms_be.content.service.impl.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import fu.fa26.sep490.g58.otcms_be.appointment.config.AppointmentWebConfiguration;
import fu.fa26.sep490.g58.otcms_be.appointment.web.PublicRequestRateLimitInterceptor;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService;
import fu.fa26.sep490.g58.otcms_be.common.web.ProductionReadinessGateInterceptor;
import fu.fa26.sep490.g58.otcms_be.config.SecurityConfig;
import fu.fa26.sep490.g58.otcms_be.config.SessionSecurityProperties;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import fu.fa26.sep490.g58.otcms_be.content.controller.*;
import fu.fa26.sep490.g58.otcms_be.content.service.*;
import fu.fa26.sep490.g58.otcms_be.content.model.*;
@WebMvcTest({
    AdminWebsiteContentController.class,AdminAnnouncementController.class,PublicClinicContentController.class
})
@Import({
    fu.fa26.sep490.g58.otcms_be.content.mapper.ClinicContentDtoMapper.class,fu.fa26.sep490.g58.otcms_be.content.HttpContractCapture.class,SecurityConfig.class,ClinicContentControllerTest.Fixture.class
})
class ClinicContentControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private ClinicContentService content;
    @MockitoBean private JwtTokenService tokens;
    @MockitoBean private AccountAccessService access;
    @MockitoBean private AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean private PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean private ProductionReadinessGateInterceptor productionReadinessGateInterceptor;
    @Test void adminReadsAndWritesAreNotAvailableToOtherRoles() throws Exception {
        for(String base:List.of("/api/v1/admin/website-contents","/api/v1/admin/announcements")) {
            mvc.perform(get(base)).andExpect(status().isUnauthorized());
            for(String role:List.of("PATIENT","OWNER","DOCTOR","RECEPTIONIST","IMAGING_TECHNICIAN")) {
                mvc.perform(get(base).with(user("other").roles(role))).andExpect(status().isForbidden());
                mvc.perform(post(base).with(user("other").roles(role)).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(content);
    }
    @Test void publicNoticesDoNotExposeRecipientOrAuthorAndPrivateIdsReturnNotFound() throws Exception {
        UUID id=UUID.randomUUID();
        var notice=new ClinicAnnouncement(id,"Closure","Clinic closed","PUBLIC",null,java.time.Instant.now(),UUID.randomUUID(),"admin",0);
        when(content.announcements("","","","",0,20,true)).thenReturn(new ContentPage<>(List.of(notice),0,20,1,1));
        mvc.perform(get("/api/v1/public/announcements")).andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].title").value("Closure"))
        .andExpect(jsonPath("$.items[0].audience").doesNotExist())
        .andExpect(jsonPath("$.items[0].publishedByName").doesNotExist());
        when(content.announcement(id,true)).thenThrow(new ContentOperationException(ContentOperationException.Reason.NOT_FOUND));
        mvc.perform(get("/api/v1/public/announcements/"+id)).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("MSG-50"));
    }
    @Test void publicImagesAreBinaryAndUnknownOrUnpublishedArticleUsesMessage50() throws Exception {
        UUID id=UUID.randomUUID();
        byte[] bytes={
            1,2,3
        };
        when(content.image(id)).thenReturn(new WebsiteImage(id,bytes,"image/png"));
        mvc.perform(get("/api/v1/public/website-images/"+id)).andExpect(status().isOk())
        .andExpect(content().contentType("image/png")).andExpect(content().bytes(bytes));
        when(content.publicArticle(id)).thenThrow(new ContentOperationException(ContentOperationException.Reason.NOT_FOUND));
        mvc.perform(get("/api/v1/public/articles/"+id)).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("MSG-50"));
    }
    @Test void invalidCriteriaAndUploadRejectionsReturnDirectMessages() throws Exception {
        when(content.list(anyString(),anyString(),anyInt(),anyInt())).thenThrow(new ContentOperationException(ContentOperationException.Reason.INVALID_CRITERIA));
        mvc.perform(get("/api/v1/admin/website-contents").with(user("admin").roles("ADMIN")))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-54"));
        when(content.upload(any(),anyString())).thenThrow(new ContentOperationException(ContentOperationException.Reason.INVALID_CONTENT));
        var file=new org.springframework.mock.web.MockMultipartFile("file","fake.png","image/png","html".getBytes());
        mvc.perform(multipart("/api/v1/admin/website-contents/images").file(file).with(user("admin").roles("ADMIN")).with(csrf()))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-69"));
    }
    @TestConfiguration
    @EnableWebSecurity
    @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture {
    }
}
