package unimagdeportes.integration.blocks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.transaction.event.TransactionalApplicationListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.support.TransactionTemplate;
import unimagdeportes.dto.block.BlockDtos.CancelBlock;
import unimagdeportes.event.AvailabilityChangedEvent;
import unimagdeportes.service.block.BlockCancellationService;
import unimagdeportes.support.ReservationTestSupport;

class BlockCancellationIntegrationTest extends ReservationTestSupport {
    @Autowired BlockCancellationService cancellations;
    @Autowired TransactionTemplate transactions;
    @Autowired ConfigurableApplicationContext context;
    private final CommittedEvents committed = new CommittedEvents();

    @BeforeEach
    void observeCommits() {
        context.addApplicationListener(committed.listener);
    }

    @AfterEach
    void stopObservingCommits() {
        context.removeApplicationListener(committed.listener);
    }

    static class CommittedEvents {
        final AtomicInteger count = new AtomicInteger();
        // The programmatic adapter receives every payload type; filter before counting.
        final TransactionalApplicationListener<PayloadApplicationEvent<Object>> listener =
                TransactionalApplicationListener.forPayload(TransactionPhase.AFTER_COMMIT, event -> {
                    if (event instanceof AvailabilityChangedEvent) count.incrementAndGet();
                });
    }

    @Test
    void cancelsWithoutReactivatingReservationOrChangingQuotaAndEquipment() throws Exception {
        String admin = administrator();
        String user = completeLogin("cancelled-owner@unimagdalena.edu.co");
        UUID space = space();
        UUID reservation = fixtureReservation(userId(user), space, "CONFIRMED",
                Instant.parse("2026-11-11T13:00:00Z"), Instant.parse("2026-11-11T14:00:00Z"));
        UUID item = equipment("Balón", 1).id();
        jdbc.update("insert into reservation_equipment values (?, ?, 1)", reservation, item);
        assertThat(send("POST", "/admin/blocks", """
                {"spaceId":"%s","type":"CLASS","reason":"Clase original",
                 "startAt":"2026-11-11T13:00:00Z","endAt":"2026-11-11T14:00:00Z"}
                """.formatted(space), admin).statusCode()).isEqualTo(201);
        UUID block = jdbc.queryForObject("select id from institutional_block where space_id = ?", UUID.class, space);
        var quotaBefore = body(send("GET", "/quota/me", null, user));
        int notices = mail.sentCount.get();
        committed.count.set(0);
        var response = send("POST", cancellation(block), "{\"reason\":\"  Clase cancelada  \"}", admin);
        assertThat(response.statusCode()).isEqualTo(200);
        var result = body(response);
        assertThat(result.get("id").asString()).isEqualTo(block.toString());
        assertThat(result.get("status").asString()).isEqualTo("CANCELLED");
        assertThat(result.get("reason").asString()).isEqualTo("Clase original");
        assertThat(result.get("cancellationReason").asString()).isEqualTo("Clase cancelada");
        assertThat(result.get("cancelledBy").asString()).isEqualTo(userId(admin).toString());
        assertThat(result.get("cancelledAt").asString()).endsWith("-05:00");
        assertThat(result.get("startAt").asString()).isEqualTo("2026-11-11T08:00:00-05:00");
        assertThat(result.get("endAt").asString()).isEqualTo("2026-11-11T09:00:00-05:00");
        assertThat(reservationStatus(reservation)).isEqualTo("CANCELLED_BY_ADMIN");
        assertThat(body(send("GET", "/quota/me", null, user))).isEqualTo(quotaBefore);
        assertThat(jdbc.queryForObject("select quantity from reservation_equipment where reservation_id = ?",
                Integer.class, reservation)).isEqualTo(1);
        assertThat(mail.sentCount.get()).isEqualTo(notices);
        assertThat(committed.count.get()).isEqualTo(1);
        assertThat(audits(block)).isEqualTo(1);
        var audit = jdbc.queryForMap("select entity_type,user_id,detail from audit_log "
                + "where action = 'BLOCK_CANCELLED' and entity_id = ?", block);
        assertThat(audit).containsEntry("entity_type", "INSTITUTIONAL_BLOCK")
                .containsEntry("user_id", userId(admin)).containsEntry("detail", "Clase cancelada");

        var calendar = body(send("GET", "/spaces/" + space + "/availability?date=2026-11-11", null, user));
        assertThat(calendar.get("slots").get(1).get("status").asString()).isEqualTo("FREE");
        assertThat(calendar.get("slots").get(1).get("reason").isNull()).isTrue();
        var occupancy = body(send("GET", "/admin/occupancy?date=2026-11-11&spaceId=" + space, null, admin));
        assertThat(occupancy.get("spaces").get(0).get("slots").get(1).get("status").asString()).isEqualTo("FREE");
        assertThat(send("POST", "/reservations", reservationRequest(space, item), user).statusCode()).isEqualTo(201);
        assertThat(reservationStatus(reservation)).isEqualTo("CANCELLED_BY_ADMIN");
    }

    @Test
    void repeatedCancellationPreservesFirstResponseEvenAfterEndAndWithAnotherAdministrator() throws Exception {
        String admin = administrator();
        UUID block = block(space(), START.plusSeconds(3600), null);
        var first = send("POST", cancellation(block), "{\"reason\":\"Primero\"}", admin);
        String other = completeLogin("other-admin@unimagdalena.edu.co");
        jdbc.update("update app_user set role = 'ADMIN' where id = ?", userId(other));
        clock.advance(Duration.ofMinutes(1));
        assertThat(send("POST", "/auth/reauth/code", null, other).statusCode()).isEqualTo(202);
        other = body(send("POST", "/auth/reauth/verify",
                "{\"code\":\"" + code("other-admin@unimagdalena.edu.co") + "\"}", other)).get("token").asString();
        committed.count.set(0);
        assertThat(body(send("POST", cancellation(block), "{\"reason\":\"Otro motivo\"}", other))).isEqualTo(body(first));
        clock.set(START.plusSeconds(7200));
        var result = cancellations.cancel(block, new CancelBlock("Después del fin"), userId(other));
        assertThat(result.cancellationReason()).isEqualTo("Primero");
        assertThat(audits(block)).isEqualTo(1);
        assertThat(committed.count.get()).isZero();
    }

    @Test
    void acceptsAnotherAdministratorOnFirstCancellation() throws Exception {
        String admin = administrator();
        UUID block = block(space(), START.plusSeconds(3600), null);
        UUID creator = fixtureUser("creator");
        fixtureAudit("BLOCK_CREATED", "INSTITUTIONAL_BLOCK", block, START);
        jdbc.update("update audit_log set user_id = ? where entity_id = ?", creator, block);
        var result = send("POST", cancellation(block), "{\"reason\":\"Clase suspendida\"}", admin);
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(body(result).get("cancelledBy").asString()).isEqualTo(userId(admin).toString());
    }

    @Test
    void listingIncludesHistoryAndEndedActiveRowsWithStablePaginationAndOverlapBoundaries() throws Exception {
        String admin = administrator();
        UUID space = space();
        Instant start = START.plusSeconds(3600);
        UUID group = UUID.randomUUID();
        UUID first = block(space, start, group);
        UUID second = block(space, start, group);
        UUID before = block(space, start.minusSeconds(3600), null);
        UUID after = block(space, start.plusSeconds(3600), null);
        cancellations.cancel(first, new CancelBlock("Histórico"), userId(admin));
        String query = listing(space, start, start.plusSeconds(3600));
        // A fresh token without reauth can still read history.
        String readOnly = completeLogin("reader-admin@unimagdalena.edu.co");
        jdbc.update("update app_user set role = 'ADMIN' where id = ?", userId(readOnly));
        var all = body(send("GET", query, null, readOnly));
        assertThat(all.get("totalElements").asLong()).isEqualTo(2);
        assertThat(all.get("page").asInt()).isZero();
        assertThat(all.get("size").asInt()).isEqualTo(20);
        assertThat(all.get("items").toString()).doesNotContain(before.toString(), after.toString());
        assertThat(all.get("items").get(0).get("recurrenceGroup").asString()).isEqualTo(group.toString());
        var page0 = body(send("GET", query + "&size=1", null, admin));
        var page1 = body(send("GET", query + "&size=1&page=1", null, admin));
        assertThat(page0.get("items").get(0)).isEqualTo(all.get("items").get(0));
        assertThat(page1.get("items").get(0)).isEqualTo(all.get("items").get(1));
        assertThat(body(send("GET", query + "&status=CANCELLED", null, admin)).get("items").get(0).get("id").asString())
                .isEqualTo(first.toString());
        clock.set(start.plusSeconds(7200));
        var active = body(send("GET", query + "&status=ACTIVE", null, admin)).get("items");
        assertThat(active.size()).isEqualTo(1);
        assertThat(active.get(0).get("id").asString()).isEqualTo(second.toString());
        assertThat(active.get(0).get("cancelledAt").isNull()).isTrue();
        assertThat(active.get(0).get("cancelledBy").isNull()).isTrue();
        assertThat(active.get(0).get("cancellationReason").isNull()).isTrue();
    }

    @Test
    void validatesFiltersBodiesIdsAndTemporalLimits() throws Exception {
        String admin = administrator();
        UUID space = space();
        UUID block = block(space, START, null);
        String query = listing(space, START, START.plusSeconds(7200));
        for (String suffix : new String[]{"&size=0", "&size=101", "&page=-1", "&status=INVALID", "&page=x"}) {
            error(send("GET", query + suffix, null, admin), 400, "VALIDATION_ERROR");
        }
        error(send("GET", "/admin/blocks", null, admin), 400, "VALIDATION_ERROR");
        error(send("GET", listing(space, START, START), null, admin), 400, "VALIDATION_ERROR");
        error(send("GET", listing(UUID.randomUUID(), START, START.plusSeconds(1)), null, admin),
                404, "SPACE_NOT_FOUND");
        error(send("GET", "/admin/blocks?spaceId=no&from=no&to=no", null, admin), 400, "VALIDATION_ERROR");
        for (String invalid : new String[]{"{}", "null", "{\"reason\":null}", "{\"reason\":\" \\t \"}", "{",
                "{\"reason\":\"" + "a".repeat(1001) + "\"}"}) {
            error(send("POST", cancellation(block), invalid, admin), 400, "VALIDATION_ERROR");
        }
        error(send("POST", cancellation(UUID.randomUUID()), "{\"reason\":\"Clase\"}", admin), 404, "BLOCK_NOT_FOUND");
        error(send("POST", "/admin/blocks/not-a-uuid/cancellation", "{\"reason\":\"Clase\"}", admin),
                400, "VALIDATION_ERROR");
        assertThat(send("POST", cancellation(block), "{\"reason\":\"En curso\"}", admin).statusCode()).isEqualTo(200);
        error(send("POST", cancellation(block), "{}", admin), 400, "VALIDATION_ERROR");
        UUID ended = block(space, clock.instant().minusSeconds(3600), null);
        error(send("POST", cancellation(ended), "{\"reason\":\"Terminó\"}", admin), 409, "BLOCK_NOT_CANCELLABLE");
        assertThat(audits(ended)).isZero();
    }

    @Test
    void enforcesCurrentRoleReauthenticationAndExpiredTokenEvenForRetries() throws Exception {
        String plainAdmin = completeLogin("admin@unimagdalena.edu.co");
        String user = completeLogin("no-access@unimagdalena.edu.co");
        UUID space = space();
        UUID block = block(space, START.plusSeconds(3600), null);
        String path = cancellation(block);
        String reason = "{\"reason\":\"Clase\"}";
        error(send("POST", path, reason, null), 401, "TOKEN_INVALID");
        error(send("GET", listing(space, START, START.plusSeconds(7200)), null, user), 403, "FORBIDDEN");
        error(send("POST", path, reason, user), 403, "FORBIDDEN");
        error(send("POST", path, reason, plainAdmin), 403, "REAUTH_REQUIRED");
        String admin = reauthenticate(plainAdmin);
        assertThat(send("POST", path, reason, admin).statusCode()).isEqualTo(200);
        clock.advance(Duration.ofMinutes(10));
        error(send("POST", path, reason, admin), 403, "REAUTH_REQUIRED");
        jdbc.update("update app_user set role = 'USER' where id = ?", userId(admin));
        error(send("POST", path, reason, admin), 403, "FORBIDDEN");
        clock.advance(Duration.ofHours(24));
        error(send("POST", path, reason, admin), 401, "TOKEN_EXPIRED");
    }

    @Test
    void cancellationLeavesSiblingsOtherBlocksAndMaintenanceIntact() throws Exception {
        String admin = administrator();
        UUID space = space();
        UUID group = UUID.randomUUID();
        UUID first = block(space, START.plusSeconds(3600), group);
        UUID overlap = block(space, START.plusSeconds(3600), null);
        UUID sibling = block(space, START.plusSeconds(7200), group);
        cancellations.cancel(first, new CancelBlock("Una franja"), userId(admin));
        var calendar = body(send("GET", "/spaces/" + space + "/availability?date=2026-11-11", null, admin));
        assertThat(calendar.get("slots").get(1).get("status").asString()).isEqualTo("BLOCKED");
        assertThat(jdbc.queryForObject("select status from institutional_block where id = ?", String.class, sibling))
                .isEqualTo("ACTIVE");
        cancellations.cancel(overlap, new CancelBlock("Otra"), userId(admin));
        jdbc.update("update space set status = 'MAINTENANCE' where id = ?", space);
        calendar = body(send("GET", "/spaces/" + space + "/availability?date=2026-11-11", null, admin));
        assertThat(calendar.get("slots").get(1).get("bookable").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("select status from space where id = ?", String.class, space))
                .isEqualTo("MAINTENANCE");
    }

    @Test
    void rollbackRemovesTransitionAuditAndCommittedEvent() throws Exception {
        String admin = administrator();
        UUID block = block(space(), START.plusSeconds(3600), null);
        committed.count.set(0);
        assertThatThrownBy(() -> transactions.executeWithoutResult(transaction -> {
            cancellations.cancel(block, new CancelBlock("No confirmar"), userId(admin));
            throw new IllegalStateException("rollback-test");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select status from institutional_block where id = ?", String.class, block))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select cancelled_at from institutional_block where id = ?",
                Timestamp.class, block)).isNull();
        assertThat(audits(block)).isZero();
        assertThat(committed.count.get()).isZero();
    }

    @Test
    @Timeout(20)
    void concurrentCancellationsReturnSameTransition() throws Exception {
        String admin = administrator();
        UUID block = block(space(), START.plusSeconds(3600), null);
        committed.count.set(0);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return send("POST", cancellation(block),
                    "{\"reason\":\"Primero\"}", admin); });
            var second = executor.submit(() -> { start.await(); return send("POST", cancellation(block),
                    "{\"reason\":\"Segundo\"}", admin); });
            start.countDown();
            var a = first.get(10, TimeUnit.SECONDS);
            var b = second.get(10, TimeUnit.SECONDS);
            assertThat(a.statusCode()).isEqualTo(200);
            assertThat(b.statusCode()).isEqualTo(200);
            assertThat(body(a)).isEqualTo(body(b));
        }
        assertThat(audits(block)).isEqualTo(1);
        assertThat(committed.count.get()).isEqualTo(1);
    }

    @Test
    @Timeout(20)
    void reservationWaitsForCancellationTransactionAndUsesCommittedAvailability() throws Exception {
        String admin = administrator();
        String user = completeLogin("racing-user@unimagdalena.edu.co");
        UUID space = space();
        UUID block = block(space, START.plusSeconds(3600), null);
        var changed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var cancellationBackend = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var cancel = executor.submit(() -> transactions.executeWithoutResult(transaction -> {
                cancellations.cancel(block, new CancelBlock("Clase cancelada"), userId(admin));
                cancellationBackend.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                changed.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("release timeout");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            }));
            assertThat(changed.await(10, TimeUnit.SECONDS)).isTrue();
            var reserve = executor.submit(() -> send("POST", "/reservations", """
                    {"spaceId":"%s","startAt":"2026-11-11T13:00:00Z","slots":1}
                    """.formatted(space), user));
            try {
                // Observe a real PostgreSQL waiter, not just a task scheduled on the executor.
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean waiting;
                do {
                    waiting = Boolean.TRUE.equals(jdbc.queryForObject("""
                            select exists (
                                select 1 from pg_locks waiter
                                where waiter.locktype = 'advisory' and not waiter.granted
                                  and waiter.objid = 1157411661::oid
                                  and ? = any(pg_blocking_pids(waiter.pid))
                            )
                            """, Boolean.class, cancellationBackend.get()));
                    if (!waiting) Thread.sleep(10);
                } while (!waiting && System.nanoTime() < deadline);
                assertThat(waiting).as("reservation waits on the cancellation transaction's advisory lock").isTrue();
                assertThat(reserve.isDone()).isFalse();
            } finally {
                release.countDown();
            }
            cancel.get(10, TimeUnit.SECONDS);
            assertThat(reserve.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(201);
        } finally {
            release.countDown();
        }
    }

    @Test
    @Timeout(15)
    void realSseStreamReceivesCancellationAfterCommit() throws Exception {
        String admin = administrator();
        UUID space = space();
        UUID block = block(space, START.plusSeconds(3600), null);
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/spaces/" + space
                + "/availability/stream")).header("Authorization", "Bearer " + admin)
                .header("Origin", "https://client.example").GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        assertThat(response.statusCode()).isEqualTo(200);
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(response.body()))) {
            assertThat(reader.readLine()).startsWith(":");
            assertThat(send("POST", cancellation(block), "{\"reason\":\"Clase cancelada\"}", admin).statusCode())
                    .isEqualTo(200);
            assertThat(reader.lines().filter(line -> line.startsWith("event:")).findFirst().orElseThrow())
                    .isEqualTo("event:availability-changed");
            assertThat(reader.readLine()).contains(space.toString()).contains("changedAt");
        }
    }

    private UUID block(UUID space, Instant start, UUID group) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into institutional_block (id,space_id,type,reason,start_at,end_at,recurrence_group) "
                        + "values (?,?,'CLASS','Clase original',?,?,?)", id, space, Timestamp.from(start),
                Timestamp.from(start.plusSeconds(3600)), group);
        return id;
    }

    private String listing(UUID space, Instant from, Instant to) {
        return "/admin/blocks?spaceId=" + space + "&from=" + from + "&to=" + to;
    }

    private String cancellation(UUID id) { return "/admin/blocks/" + id + "/cancellation"; }

    private int audits(UUID block) {
        return jdbc.queryForObject("select count(*) from audit_log where action = 'BLOCK_CANCELLED' and entity_id = ?",
                Integer.class, block);
    }

    private String reservationStatus(UUID id) {
        return jdbc.queryForObject("select status from reservation where id = ?", String.class, id);
    }

    private String reservationRequest(UUID space, UUID equipment) {
        return """
                {"spaceId":"%s","startAt":"2026-11-11T13:00:00Z","slots":1,
                 "equipment":[{"equipmentId":"%s","quantity":1}]}
                """.formatted(space, equipment);
    }
}
