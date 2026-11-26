package unimagdeportes.integration.blocks;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import unimagdeportes.service.block.BlockQueryService;
import unimagdeportes.support.ReservationTestSupport;

class BlockActorNamesIntegrationTest extends ReservationTestSupport {
    @Autowired BlockQueryService query;
    @Autowired jakarta.persistence.EntityManagerFactory entities;

    @Test
    void listAndCancellationShowDifferentActorsWithoutFilteringFormerAdminRole() throws Exception {
        String admin = administrator();
        UUID creator = namedUser("Ana Martínez");
        UUID space = space(), block = block(space);
        creation(block, creator);
        jdbc.update("update app_user set name = 'Carlos Pérez' where id = ?", userId(admin));

        var active = first(space, admin);
        assertThat(active.get("createdBy").asString()).isEqualTo(creator.toString());
        assertThat(active.get("createdByName").asString()).isEqualTo("Ana Martínez");
        assertThat(active.get("cancelledByName").isNull()).isTrue();
        var response = send("POST", cancellation(block), "{\"reason\":\"Clase suspendida\"}", admin);
        assertThat(response.statusCode()).isEqualTo(200);
        var cancelled = body(response);
        assertThat(cancelled.get("createdByName").asString()).isEqualTo("Ana Martínez");
        assertThat(cancelled.get("cancelledByName").asString()).isEqualTo("Carlos Pérez");
        assertThat(cancelled.get("createdBy").asString()).isEqualTo(creator.toString());
        assertThat(cancelled.get("cancelledBy").asString()).isEqualTo(userId(admin).toString());
        assertThat(first(space, admin)).isEqualTo(cancelled);
        assertThat(cancelled.toString()).doesNotContain("@unimagdalena.edu.co", "\"email\"", "\"role\"");
    }

    @Test
    void profileChangesUpdateNamesOnRetryWithoutChangingOriginalCancellationOrAudit() throws Exception {
        String admin = administrator();
        UUID creator = namedUser("Ana");
        UUID space = space(), block = block(space);
        creation(block, creator);
        var first = send("POST", cancellation(block), "{\"reason\":\"Primero\"}", admin);
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(send("PUT", "/me/profile", "{\"name\":\"Carlos Actualizado\"}", admin).statusCode()).isEqualTo(200);
        jdbc.update("update app_user set name = 'Ana Actualizada', role = 'USER' where id = ?", creator);
        var repeated = send("POST", cancellation(block), "{\"reason\":\"Otro motivo\"}", admin);
        assertThat(repeated.statusCode()).isEqualTo(200);
        var result = body(repeated);
        assertThat(result.get("createdByName").asString()).isEqualTo("Ana Actualizada");
        assertThat(result.get("cancelledByName").asString()).isEqualTo("Carlos Actualizado");
        for (String field : new String[]{"id", "createdBy", "cancelledBy", "cancelledAt", "cancellationReason",
                "reason", "status", "startAt", "endAt", "recurrenceGroup"}) {
            assertThat(result.get(field)).as(field).isEqualTo(body(first).get(field));
        }
        assertThat(first(space, admin)).isEqualTo(result);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'BLOCK_CANCELLED' "
                + "and entity_id = ?", Integer.class, block)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select user_id from audit_log where action = 'BLOCK_CREATED' "
                + "and entity_id = ?", UUID.class, block)).isEqualTo(creator);
    }

    @Test
    void missingAuditsOrNamelessCreatorsDoNotBreakHistoryOrInventNames() throws Exception {
        String admin = administrator();
        UUID space = space(), block = block(space);
        var unknown = first(space, admin);
        assertThat(unknown.get("createdBy").isNull()).isTrue();
        assertThat(unknown.get("createdByName").isNull()).isTrue();
        UUID creator = namedUser("sin nombre");
        creation(block, creator);
        for (String name : new String[]{null, "  "}) {
            jdbc.update("update app_user set name = ? where id = ?", name, creator);
            var nameless = first(space, admin);
            assertThat(nameless.get("createdBy").asString()).isEqualTo(creator.toString());
            assertThat(nameless.get("createdByName").isNull()).isTrue();
        }
    }

    @Test
    void sameActorDuplicatesAreAcceptedButNullAndConflictingCreationActorsAreUnknown() throws Exception {
        String admin = administrator();
        UUID space = space(), block = block(space);
        UUID creator = namedUser("Ana");
        creation(block, creator);
        creation(block, creator);
        fixtureAudit("BLOCK_CANCELLED", "INSTITUTIONAL_BLOCK", block, START);
        fixtureAudit("BLOCK_CREATED", "RESERVATION", block, START);
        assertThat(first(space, admin).get("createdBy").asString()).isEqualTo(creator.toString());
        creation(block, null);
        assertThat(first(space, admin).get("createdBy").isNull()).isTrue();
        assertThat(first(space, admin).get("createdByName").isNull()).isTrue();
        jdbc.update("delete from audit_log where entity_id = ? and user_id is null", block);
        creation(block, namedUser("Carlos"));
        assertThat(first(space, admin).get("createdBy").isNull()).isTrue();
        assertThat(first(space, admin).get("createdByName").isNull()).isTrue();
    }

    @Test
    void cancelledActorWithoutNameDoesNotFallBackToUuidAndIdentitySurvivesRoleChange() throws Exception {
        String admin = administrator();
        UUID space = space(), block = block(space);
        UUID canceller = namedUser("Carlos");
        jdbc.update("update institutional_block set status='CANCELLED',cancelled_by=?,"
                + "cancelled_at=?,cancellation_reason='Clase suspendida' where id=?", canceller, Timestamp.from(START), block);
        jdbc.update("update app_user set name = null where id = ?", canceller);
        var result = first(space, admin);
        assertThat(result.get("cancelledBy").asString()).isEqualTo(canceller.toString());
        assertThat(result.get("cancelledByName").isNull()).isTrue();
        jdbc.update("update app_user set name = 'Carlos Actual' where id = ?", canceller);
        assertThat(first(space, admin).get("cancelledByName").asString()).isEqualTo("Carlos Actual");
    }

    @Test
    void pageEnrichmentUsesConstantQueryCountForTenAndTwentyDistinctCreatorsAndCancellers() {
        UUID space = space();
        for (int index = 0; index < 25; index++) {
            UUID block = block(space);
            creation(block, fixtureUser("creador-" + index));
            UUID canceller = fixtureUser("cancelador-" + index);
            jdbc.update("update institutional_block set status='CANCELLED',cancelled_by=?,"
                    + "cancelled_at=?,cancellation_reason='Clase suspendida' where id=?",
                    canceller, Timestamp.from(START), block);
        }
        var statistics = entities.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            var ten = query.list(filter(space, 10));
            long tenQueries = statistics.getPrepareStatementCount();
            statistics.clear();
            var twenty = query.list(filter(space, 20));
            long twentyQueries = statistics.getPrepareStatementCount();
            assertThat(ten.items()).hasSize(10);
            assertThat(twenty.items()).hasSize(20);
            assertThat(twenty.totalElements()).isEqualTo(25);
            assertThat(twenty.items()).allSatisfy(item -> {
                assertThat(item.createdByName()).startsWith("creador-");
                assertThat(item.cancelledByName()).startsWith("cancelador-");
            });
            assertThat(twentyQueries).as("doubling actors must not add per-row queries").isEqualTo(tenQueries);
            assertThat(twentyQueries).isLessThan(10);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    private BlockQueryService.Filter filter(UUID space, int size) {
        return new BlockQueryService.Filter(space, START, START.plusSeconds(10800), null, 0, size);
    }

    private UUID namedUser(String name) {
        UUID user = fixtureUser(UUID.randomUUID().toString());
        jdbc.update("update app_user set name = ? where id = ?", name, user);
        return user;
    }

    private JsonNode first(UUID space, String admin) throws Exception {
        var response = send("GET", "/admin/blocks?spaceId=" + space
                + "&from=2026-11-11T12:00:00Z&to=2026-11-11T15:00:00Z", null, admin);
        assertThat(response.statusCode()).isEqualTo(200);
        return body(response).get("items").get(0);
    }

    private UUID block(UUID space) {
        UUID block = UUID.randomUUID();
        jdbc.update("insert into institutional_block (id,space_id,type,reason,start_at,end_at) "
                + "values (?,?,'CLASS','Clase',?,?)", block, space, Timestamp.from(START.plusSeconds(3600)),
                Timestamp.from(START.plusSeconds(7200)));
        return block;
    }

    private void creation(UUID block, UUID actor) {
        jdbc.update("insert into audit_log (id,action,entity_type,entity_id,user_id,created_at) "
                + "values (?,'BLOCK_CREATED','INSTITUTIONAL_BLOCK',?,?,?)", UUID.randomUUID(), block, actor,
                Timestamp.from(START));
    }

    private String cancellation(UUID block) { return "/admin/blocks/" + block + "/cancellation"; }
}
