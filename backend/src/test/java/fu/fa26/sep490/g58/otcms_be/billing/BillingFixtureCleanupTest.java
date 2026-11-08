package fu.fa26.sep490.g58.otcms_be.billing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/** Executes teardown against a JDBC double only: no Spring context or database connection. */
class BillingFixtureCleanupTest {
    private final BillingInvoicePostgresIntegrationTest fixture = new BillingInvoicePostgresIntegrationTest();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private static final Pattern DELETE_TABLE = Pattern.compile("^delete from ([a-z_]+) where ");
    private record Deletion(String table, List<Object> ids) { }

    private UUID id(String field) { return (UUID) ReflectionTestUtils.getField(fixture, field); }

    private List<Deletion> cleanup(List<UUID> ownedInvoices) {
        fixture.jdbc = jdbc;
        String ownedQuery = "select id from invoices where created_by_account_id = ? and patient_profile_id = ?";
        when(jdbc.queryForList(ownedQuery, UUID.class, id("receptionistAccountId"), id("patientProfileId")))
                .thenReturn(ownedInvoices);
        fixture.removeOnlyFixturesCreatedByThisTest();
        verify(jdbc).queryForList(ownedQuery, UUID.class, id("receptionistAccountId"), id("patientProfileId"));
        var allowed = new HashSet<UUID>(ownedInvoices);
        for (String field : List.of("patientAccountId", "receptionistAccountId", "doctorAccountId", "patientProfileId",
                "doctorProfileId", "serviceId", "visitId", "appointmentId", "clinicalRoundId")) allowed.add(id(field));
        return mockingDetails(jdbc).getInvocations().stream().filter(call -> call.getMethod().getName().equals("update"))
                .map(call -> {
                    String sql = call.getArgument(0);
                    var table = DELETE_TABLE.matcher(sql);
                    assertTrue(table.find(), "every deletion must have an explicit WHERE clause: " + sql);
                    assertFalse(sql.contains(" like "), "cleanup must not sweep other runs by name or email");
                    assertTrue(sql.contains("?"), "fixture IDs must be bound parameters");
                    var ids = Arrays.stream(call.getArguments()).skip(1).toList();
                    assertFalse(ids.isEmpty());
                    assertTrue(ids.stream().allMatch(allowed::contains), "every bound value must be an owned fixture UUID");
                    return new Deletion(table.group(1), ids);
                }).toList();
    }

    private int position(List<Deletion> deletes, String table, UUID id) {
        for (int index = 0; index < deletes.size(); index++) {
            if (deletes.get(index).table().equals(table) && deletes.get(index).ids().contains(id)) return index;
        }
        fail("missing owned deletion for " + table);
        return -1;
    }

    @Test void allOwnedInvoiceSourcesDeleteChildrenBeforeParentsUsingOnlyFixtureIds() {
        // Returned IDs represent the Visit, Appointment and Clinical Round invoices of this fixture.
        var invoices = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var deletes = cleanup(invoices);
        int round = position(deletes, "clinical_ordering_rounds", id("clinicalRoundId"));
        int service = position(deletes, "clinic_services", id("serviceId"));
        for (UUID invoice : invoices) {
            int parent = position(deletes, "invoices", invoice);
            assertTrue(position(deletes, "payment_attempts", invoice) < parent);
            assertTrue(position(deletes, "invoice_items", invoice) < parent);
            assertTrue(parent < round);
            assertTrue(parent < service);
        }
        assertTrue(position(deletes, "clinical_service_orders", id("clinicalRoundId")) < round);
        assertTrue(round < position(deletes, "visits", id("visitId")));
        assertTrue(position(deletes, "visits", id("visitId")) < position(deletes, "appointments", id("appointmentId")));
        assertTrue(position(deletes, "appointments", id("appointmentId")) < service);
        assertTrue(position(deletes, "doctor_services", id("serviceId")) < service);
        assertTrue(service < position(deletes, "doctor_profiles", id("doctorProfileId")));
        assertTrue(position(deletes, "patient_profiles", id("patientProfileId")) < position(deletes, "accounts", id("patientAccountId")));
        assertTrue(position(deletes, "doctor_profiles", id("doctorProfileId")) < position(deletes, "accounts", id("doctorAccountId")));
        assertEquals(Set.of("payment_attempts", "invoice_items", "invoices", "clinical_service_orders", "clinical_ordering_rounds",
                "visits", "appointments", "doctor_services", "clinic_services", "doctor_profiles", "patient_profiles", "accounts"),
                deletes.stream().map(Deletion::table).collect(java.util.stream.Collectors.toSet()));
    }

    @Test void missingOwnedInvoicesNeverCausesUnscopedBillingDeletes() {
        var deletes = cleanup(List.of());
        assertTrue(deletes.stream().noneMatch(row -> Set.of("payment_attempts", "invoice_items", "invoices").contains(row.table())));
        assertTrue(position(deletes, "clinical_service_orders", id("clinicalRoundId"))
                < position(deletes, "clinical_ordering_rounds", id("clinicalRoundId")));
        assertTrue(position(deletes, "doctor_services", id("serviceId")) < position(deletes, "clinic_services", id("serviceId")));
    }
}
