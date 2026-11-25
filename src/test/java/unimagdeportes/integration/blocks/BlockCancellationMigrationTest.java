package unimagdeportes.integration.blocks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class BlockCancellationMigrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

    @Test
    void upgradesCurrentV1WithoutLosingBlocksReservationsOrAuditAndEnforcesMetadata() throws SQLException {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target("1").load().migrate();
        UUID admin = UUID.randomUUID();
        UUID space = UUID.randomUUID();
        UUID preview = UUID.randomUUID();
        UUID panorama = UUID.randomUUID();
        UUID block = UUID.randomUUID();
        UUID reservation = UUID.randomUUID();
        UUID group = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.createStatement()) {
            statement.executeUpdate("insert into app_user (id,email,role,created_at) values ('" + admin
                    + "','migration@unimagdalena.edu.co','ADMIN',now())");
            statement.executeUpdate("insert into space_image (id,content_type,storage_key,size_bytes) values ('"
                    + preview + "','image/png','preview',1),('" + panorama + "','image/png','panorama',1)");
            statement.executeUpdate("insert into space (id,name,capacity,opening_time,closing_time,status,"
                    + "preview_image_id,panorama_image_id) values ('" + space
                    + "','Cancha',10,'07:00','11:00','ACTIVE','" + preview + "','" + panorama + "')");
            statement.executeUpdate("insert into institutional_block (id,space_id,type,reason,start_at,end_at,recurrence_group)"
                    + " values ('" + block + "','" + space + "','CLASS','Original',"
                    + "'2026-11-11T13:00:00Z','2026-11-11T14:00:00Z','" + group + "')");
            statement.executeUpdate("insert into reservation (id,user_id,space_id,start_at,end_at,status,created_at,"
                    + "cancellation_threshold_minutes,checkin_opening_minutes) values ('" + reservation + "','" + admin
                    + "','" + space + "','2026-11-11T13:00:00Z','2026-11-11T14:00:00Z','CANCELLED_BY_ADMIN',now(),60,10)");
            statement.executeUpdate("insert into audit_log (id,action,entity_type,entity_id,user_id,created_at) values ('"
                    + UUID.randomUUID() + "','BLOCK_CREATED','INSTITUTIONAL_BLOCK','" + block + "','" + admin + "',now())");
        }

        var migration = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load().migrate();
        assertThat(migration.migrationsExecuted).isEqualTo(1);
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("select * from institutional_block where id = '" + block + "'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("status")).isEqualTo("ACTIVE");
                assertThat(result.getString("reason")).isEqualTo("Original");
                assertThat(result.getObject("recurrence_group")).isEqualTo(group);
                assertThat(result.getTimestamp("cancelled_at")).isNull();
                assertThat(result.getObject("cancelled_by")).isNull();
                assertThat(result.getString("cancellation_reason")).isNull();
            }
            try (var result = statement.executeQuery("select status from reservation where id = '" + reservation + "'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("CANCELLED_BY_ADMIN");
            }
            try (var result = statement.executeQuery("select count(*) from audit_log where entity_id = '" + block + "'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
            for (String set : new String[]{"status = 'CANCELLED'", "status = 'OTHER'",
                    "cancelled_at = now()", "cancellation_reason = ' '",
                    "status = 'CANCELLED',cancelled_at = now(),cancelled_by = '" + admin
                            + "',cancellation_reason = ' motivo '"}) {
                assertThatThrownBy(() -> statement.executeUpdate("update institutional_block set " + set
                        + " where id = '" + block + "'")).isInstanceOf(SQLException.class)
                        .extracting(error -> ((SQLException) error).getSQLState()).isEqualTo("23514");
            }
            statement.executeUpdate("update institutional_block set status = 'CANCELLED',cancelled_at = now(),"
                    + "cancelled_by = '" + admin + "',cancellation_reason = 'Clase cancelada' where id = '" + block + "'");
            assertThatThrownBy(() -> statement.executeUpdate("update institutional_block set cancelled_by = '"
                    + UUID.randomUUID() + "' where id = '" + block + "'")).isInstanceOf(SQLException.class)
                    .extracting(error -> ((SQLException) error).getSQLState()).isEqualTo("23503");
            try (var result = statement.executeQuery("select indexdef from pg_indexes where "
                    + "indexname = 'institutional_block_space_status_start_id_idx'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).contains("(space_id, status, start_at, id)");
            }
        }
        assertThat(Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load().migrate().migrationsExecuted).isZero();
    }
}
