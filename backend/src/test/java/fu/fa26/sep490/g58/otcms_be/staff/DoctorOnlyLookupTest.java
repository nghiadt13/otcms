package fu.fa26.sep490.g58.otcms_be.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorDirectoryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffDirectoryQueryService;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * final-db V110 put doctors and other staff in one staff_profiles table. A staff profile whose is_doctor flag is
 * false (for example a receptionist) must never be returned by a doctor lookup.
 */
@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
class DoctorOnlyLookupTest {
    private static final String EMAIL_PREFIX = "t110-doctor-only-";

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired DoctorProfileIdentityQueryService identity;
    @Autowired DoctorProfileQueryService profileQuery;
    @Autowired DoctorDirectoryService directory;
    @Autowired StaffDirectoryQueryService staffDirectory;

    @AfterEach
    void cleanup() {
        jdbc.update("delete from work_shifts where staff_profile_id in "
                + "(select id from staff_profiles where account_id in (select id from accounts where normalized_email like ?))",
                EMAIL_PREFIX + "%");
        jdbc.update("delete from staff_profiles where account_id in (select id from accounts where normalized_email like ?)",
                EMAIL_PREFIX + "%");
        jdbc.update("delete from accounts where normalized_email like ?", EMAIL_PREFIX + "%");
    }

    @Test
    void nonDoctorStaffProfileIsNeverReturnedByIdentityOrDirectoryLookups() throws Exception {
        UUID receptionistAccount = account("RECEPTIONIST");
        UUID receptionistProfile = profile(receptionistAccount, "T110 Receptionist", false);
        UUID doctorAccount = account("DOCTOR");
        UUID doctorProfile = profile(doctorAccount, "T110 Doctor", true);

        // Control: the doctor profile is found by every doctor lookup.
        assertEquals(doctorProfile, identity.findDoctorProfileId(doctorAccount).orElseThrow());
        assertEquals(doctorAccount, identity.findDoctorAccountId(doctorProfile).orElseThrow());
        assertEquals("T110 Doctor", identity.findDoctorNames(List.of(doctorProfile)).get(doctorProfile));
        assertTrue(profileQuery.existsDoctorProfile(doctorProfile));
        assertTrue(directory.listPublic().stream().anyMatch(profile -> profile.id().equals(doctorProfile)));

        // The receptionist's staff profile is invisible to the same lookups.
        assertTrue(identity.findDoctorProfileId(receptionistAccount).isEmpty());
        assertTrue(identity.findDoctorAccountId(receptionistProfile).isEmpty());
        assertTrue(identity.findDoctorNames(List.of(receptionistProfile)).isEmpty());
        assertFalse(profileQuery.existsDoctorProfile(receptionistProfile));
        assertTrue(directory.listPublic().stream().noneMatch(profile -> profile.id().equals(receptionistProfile)));
        assertTrue(directory.findPublic(receptionistProfile).isEmpty());
        assertNull(staffDirectory.findDoctorByAccount(receptionistAccount,
                Instant.parse("2026-11-01T09:00:00Z"), Instant.parse("2026-11-01T10:00:00Z")));
        mvc.perform(get("/api/v1/doctors/{id}", receptionistProfile)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/doctors")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.doctorProfileId == '" + receptionistProfile + "')]").doesNotExist())
                .andExpect(jsonPath("$.items[?(@.doctorProfileId == '" + doctorProfile + "')]").exists());
    }

    @Test
    void ownShiftsEndpointOnlyResolvesDoctorProfiles() throws Exception {
        // A DOCTOR-role account that only has a non-doctor staff profile has no doctor profile.
        UUID strayAccount = account("DOCTOR");
        profile(strayAccount, "T110 Stray", false);
        mvc.perform(get("/api/v1/doctors/me/shifts").param("from", "2026-11-01T00:00:00Z").param("to", "2026-11-02T00:00:00Z")
                        .with(actor(AccountRole.DOCTOR, strayAccount)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DOCTOR_PROFILE_NOT_FOUND"));

        UUID doctorAccount = account("DOCTOR");
        UUID doctorProfile = profile(doctorAccount, "T110 Doctor Shifts", true);
        mvc.perform(get("/api/v1/doctors/me/shifts").param("from", "2026-11-01T00:00:00Z").param("to", "2026-11-02T00:00:00Z")
                        .with(actor(AccountRole.DOCTOR, doctorAccount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        assertTrue(profileQuery.existsDoctorProfile(doctorProfile));
    }

    private RequestPostProcessor actor(AccountRole role, UUID accountId) {
        var principal = new AuthenticatedAccount(accountId, role, UUID.randomUUID());
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private UUID account(String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) "
                + "values (?, ?, ?, 'ACTIVE', now(), now())", id, EMAIL_PREFIX + id + "@example.test", type);
        return id;
    }

    private UUID profile(UUID accountId, String name, boolean doctor) {
        UUID id = UUID.randomUUID();
        // Doctors start public so the control assertions can see them in the directory.
        jdbc.update("insert into staff_profiles (id, account_id, display_name, is_doctor, publicly_visible) "
                + "values (?, ?, ?, ?, ?)", id, accountId, name, doctor, doctor);
        return id;
    }
}
