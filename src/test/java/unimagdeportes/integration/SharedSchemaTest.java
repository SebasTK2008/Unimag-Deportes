package unimagdeportes.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class SharedSchemaTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load().migrate();
    }

    @Test
    void overlappingReservationsInSameSpaceAreRejected() throws SQLException {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.createStatement()) {
            UUID firstUser = UUID.randomUUID();
            UUID secondUser = UUID.randomUUID();
            UUID space = UUID.randomUUID();
            statement.executeUpdate("INSERT INTO app_user (id,email,role,created_at) VALUES ('" + firstUser
                    + "','first@unimagdalena.edu.co','USER',now()),('" + secondUser
                    + "','second@unimagdalena.edu.co','USER',now())");
            statement.executeUpdate("INSERT INTO space (id,name,capacity,opening_time,closing_time,status) VALUES ('"
                    + space + "','Cancha',10,'08:00','20:00','ACTIVE')");
            String values = "','" + space + "','2026-11-11 14:00:00-05','2026-11-11 15:00:00-05',"
                    + "'CONFIRMED',now(),60,10)";
            String insert = "INSERT INTO reservation (id,user_id,space_id,start_at,end_at,status,created_at,"
                    + "cancellation_threshold_minutes,checkin_opening_minutes) VALUES ('";
            statement.executeUpdate(insert + UUID.randomUUID() + "','" + firstUser + values);
            assertThatThrownBy(() -> statement.executeUpdate(insert + UUID.randomUUID() + "','" + secondUser + values))
                    .isInstanceOf(SQLException.class).extracting(error -> ((SQLException) error).getSQLState())
                    .isEqualTo("23P01");
            try (var result = statement.executeQuery("SELECT p.weekly_quota_hours FROM usage_parameters p "
                    + "JOIN usage_parameters_head h ON h.current_version_id = p.id WHERE h.id = 1")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(4);
            }
        }
    }

    @Test
    void initialParameterVersionHasDefaultsAndExactlyOneHead() throws SQLException {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.createStatement();
                var result = statement.executeQuery("select p.* from usage_parameters p "
                        + "join usage_parameters_head h on h.current_version_id = p.id where h.id = 1")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getLong("version_number")).isEqualTo(1);
            assertThat(result.getInt("weekly_quota_hours")).isEqualTo(4);
            assertThat(result.getInt("no_show_threshold")).isEqualTo(3);
            assertThat(result.getObject("created_by")).isNull();
            assertThat(result.getTimestamp("created_at")).isNotNull();
            assertThat(result.next()).isFalse();
        }
    }

    @Test
    void parameterVersionsCannotBeOverwrittenOrDeleted() throws SQLException {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.createStatement()) {
            for (String sql : new String[]{"update usage_parameters set weekly_quota_hours = 8 where version_number = 1",
                    "delete from usage_parameters where version_number = 1"}) {
                assertThatThrownBy(() -> statement.executeUpdate(sql)).isInstanceOf(SQLException.class)
                        .extracting(error -> ((SQLException) error).getSQLState()).isEqualTo("23000");
            }
            assertThatThrownBy(() -> statement.executeUpdate("update usage_parameters_head set current_version_id = '"
                    + UUID.randomUUID() + "' where id = 1")).isInstanceOf(SQLException.class)
                    .extracting(error -> ((SQLException) error).getSQLState()).isEqualTo("23503");
        }
    }

    @Test
    void overlappingReservationsForSameUserInDifferentSpacesAreRejected() throws SQLException {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.createStatement()) {
            UUID user = UUID.randomUUID();
            UUID firstSpace = UUID.randomUUID();
            UUID secondSpace = UUID.randomUUID();
            statement.executeUpdate("INSERT INTO app_user (id,email,role,created_at) VALUES ('" + user
                    + "','" + user + "@unimagdalena.edu.co','USER',now())");
            statement.executeUpdate("INSERT INTO space (id,name,capacity,opening_time,closing_time,status) VALUES ('"
                    + firstSpace + "','Cancha A',10,'08:00','20:00','ACTIVE'),('" + secondSpace
                    + "','Cancha B',10,'08:00','20:00','ACTIVE')");
            String insert = "INSERT INTO reservation (id,user_id,space_id,start_at,end_at,status,created_at,"
                    + "cancellation_threshold_minutes,checkin_opening_minutes) VALUES ('";
            String values = "','2026-11-11 14:00:00-05','2026-11-11 15:00:00-05','CONFIRMED',now(),60,10)";
            statement.executeUpdate(insert + UUID.randomUUID() + "','" + user + "','" + firstSpace + values);
            assertThatThrownBy(() -> statement.executeUpdate(insert + UUID.randomUUID() + "','" + user + "','"
                    + secondSpace + values)).isInstanceOf(SQLException.class)
                    .extracting(error -> ((SQLException) error).getSQLState()).isEqualTo("23P01");
        }
    }
}
