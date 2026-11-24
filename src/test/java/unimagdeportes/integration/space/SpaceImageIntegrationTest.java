package unimagdeportes.integration.space;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import unimagdeportes.storage.StorageObjectCleanup;
import unimagdeportes.support.RulesTestSupport;

class SpaceImageIntegrationTest extends RulesTestSupport {
    @Autowired private StorageObjectCleanup cleanup;
    private static final String SPACE = """
            {"name":"Cancha","capacity":10,"openingTime":"06:00","closingTime":"22:00",
             "disciplines":["TENNIS"]}
            """;
    private static final String BOUNDARY = "UnimagBoundary";

    @Test
    void createsSpaceAndReplacesImagesIndependentlyWithPermissions() throws Exception {
        String admin = administrator();
        String user = completeLogin("usuario@unimagdalena.edu.co");
        byte[] preview = image(0xff0000);
        byte[] panorama = image(0x00ff00);
        byte[] replacement = image(0x0000ff);
        var created = multipart("POST", "/admin/spaces", admin, SPACE, preview, panorama, null);

        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        String id = body(created).get("id").asString();
        assertThat(imageStorage.objects).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from space_image where storage_key in (?, ?)",
                Integer.class, imageStorage.objects.keySet().toArray())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns "
                + "where table_schema = 'public' and table_name = 'space_image' and column_name = 'content'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from storage_object_cleanup", Integer.class)).isZero();
        assertThat(send("GET", "/admin/spaces", null, admin).body()).doesNotContain("content", "previewImage");
        assertThat(imageResponse(id, "PREVIEW", user).body()).containsExactly(preview);
        assertThat(imageResponse(id, "PANORAMA", user).body()).containsExactly(panorama);
        cleanup.clean();
        assertThat(imageStorage.objects).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from storage_object_cleanup", Integer.class)).isZero();
        assertThat(imageResponse(id, "PREVIEW", null).statusCode()).isEqualTo(401);

        var forbidden = multipart("PUT", "/admin/spaces/" + id + "/images/PREVIEW",
                user, null, null, null, replacement);
        assertThat(forbidden.statusCode()).isEqualTo(403);
        var updated = multipart("PUT", "/admin/spaces/" + id + "/images/PREVIEW",
                admin, null, null, null, replacement);
        assertThat(updated.statusCode()).as(updated.body()).isEqualTo(204);
        assertThat(imageResponse(id, "PREVIEW", user).body()).containsExactly(replacement);
        assertThat(imageResponse(id, "PANORAMA", user).body()).containsExactly(panorama);
        assertThat(imageStorage.objects).hasSize(3);
        cleanup.clean();
        assertThat(imageStorage.objects).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from storage_object_cleanup", Integer.class)).isZero();
    }

    @Test
    void rejectsIncompatibleFootballOnCreateAndUpdateWithoutPersistingChanges() throws Exception {
        String admin = administrator();
        byte[] image = image(0xff0000);
        String incompatible = SPACE.replace("[\"TENNIS\"]", "[\"FOOTBALL_5\",\"FOOTBALL_11\"]");
        int before = jdbc.queryForObject("select count(*) from space", Integer.class);

        var rejected = multipart("POST", "/admin/spaces", admin, incompatible, image, image, null);
        assertThat(rejected.statusCode()).as(rejected.body()).isEqualTo(400);
        assertThat(body(rejected).get("code").asString()).isEqualTo("VALIDATION_ERROR");
        assertThat(body(rejected).get("details").get("fields").get("disciplines").asString())
                .contains("Fútbol 5", "Fútbol 11");
        assertThat(jdbc.queryForObject("select count(*) from space", Integer.class)).isEqualTo(before);

        var created = multipart("POST", "/admin/spaces", admin,
                SPACE.replace("TENNIS", "FOOTBALL_5"), image, image, null);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        String id = body(created).get("id").asString();
        var updated = send("PUT", "/admin/spaces/" + id, incompatible, admin);
        assertThat(updated.statusCode()).as(updated.body()).isEqualTo(400);
        assertThat(jdbc.queryForList("select discipline from space_discipline where space_id = ?",
                String.class, java.util.UUID.fromString(id))).containsExactly("FOOTBALL_5");

        var valid = send("PUT", "/admin/spaces/" + id,
                "{\"disciplines\":[\"BASKETBALL\",\"VOLLEYBALL\"]}", admin);
        assertThat(valid.statusCode()).as(valid.body()).isEqualTo(200);
        assertThat(valid.body()).contains("BASKETBALL", "VOLLEYBALL");
    }

    @Test
    void rejectsCreationWithoutBothImagesOrWithInvalidContent() throws Exception {
        String admin = administrator();
        byte[] image = image(0xff0000);
        int before = jdbc.queryForObject("select count(*) from space where name = 'Cancha'", Integer.class);

        assertThat(multipart("POST", "/admin/spaces", admin, SPACE, image, null, null).statusCode()).isEqualTo(400);
        assertThat(multipart("POST", "/admin/spaces", admin, SPACE, image, new byte[]{1, 2}, null).statusCode())
                .isEqualTo(400);
        assertThat(jdbc.queryForObject("select count(*) from space where name = 'Cancha'", Integer.class))
                .isEqualTo(before);
        assertThat(imageStorage.objects).isEmpty();
    }

    @Test
    void failedSecondUploadLeavesNoReferencesAndDurableCleanupSurvivesStorageFailure() throws Exception {
        String admin = administrator();
        int before = jdbc.queryForObject("select count(*) from space", Integer.class);
        imageStorage.failUploadAt = 2;
        error(multipart("POST", "/admin/spaces", admin, SPACE, image(1), image(2), null),
                503, "STORAGE_UNAVAILABLE");
        assertThat(jdbc.queryForObject("select count(*) from space", Integer.class)).isEqualTo(before);
        assertThat(imageStorage.objects).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from storage_object_cleanup", Integer.class)).isEqualTo(2);
        cleanup.clean();
        assertThat(imageStorage.objects).hasSize(2); // Upload grace period protects in-flight work.
        clock.advance(Duration.ofHours(1));
        imageStorage.failDelete = true;
        cleanup.clean();
        assertThat(jdbc.queryForObject("select count(*) from storage_object_cleanup", Integer.class)).isEqualTo(2);
        imageStorage.failDelete = false;
        clock.advance(Duration.ofMinutes(5));
        cleanup.clean();
        assertThat(imageStorage.objects).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from storage_object_cleanup", Integer.class)).isZero();
    }

    @Test
    void databaseFailureRollsBackReferencesAndPreservesPreviousImageOnReplacement() throws Exception {
        String admin = administrator();
        var created = multipart("POST", "/admin/spaces", admin, SPACE, image(1), image(2), null);
        assertThat(created.statusCode()).isEqualTo(201);
        String id = body(created).get("id").asString();
        Set<String> originalKeys = Set.copyOf(imageStorage.objects.keySet());
        int before = jdbc.queryForObject("select count(*) from space", Integer.class);
        jdbc.execute("create function reject_space_audit() returns trigger language plpgsql as $$ "
                + "begin if NEW.action in ('SPACE_CREATED', 'SPACE_IMAGE_UPDATED') then "
                + "raise exception 'test audit failure'; end if; return NEW; end $$");
        jdbc.execute("create trigger reject_space before insert on audit_log "
                + "for each row execute function reject_space_audit()");
        try {
            assertThat(multipart("POST", "/admin/spaces", admin, SPACE, image(3), image(4), null).statusCode())
                    .isEqualTo(500);
            assertThat(multipart("PUT", "/admin/spaces/" + id + "/images/PREVIEW",
                    admin, null, null, null, image(5)).statusCode()).isEqualTo(500);
        } finally {
            jdbc.execute("drop trigger reject_space on audit_log");
            jdbc.execute("drop function reject_space_audit()");
        }
        assertThat(jdbc.queryForObject("select count(*) from space", Integer.class)).isEqualTo(before);
        assertThat(imageResponse(id, "PREVIEW", admin).body()).containsExactly(image(1));
        clock.advance(Duration.ofHours(1));
        cleanup.clean();
        assertThat(imageStorage.objects.keySet()).containsExactlyInAnyOrderElementsOf(originalKeys);
    }

    @Test
    void cleanupNeverDeletesAReferencedObjectAndReadFailureIsControlled() throws Exception {
        String admin = administrator();
        var created = multipart("POST", "/admin/spaces", admin, SPACE, image(1), image(2), null);
        assertThat(created.statusCode()).isEqualTo(201);
        String id = body(created).get("id").asString();
        String key = imageStorage.objects.keySet().iterator().next();
        jdbc.update("insert into storage_object_cleanup (storage_key,not_before) values (?, ?)",
                key, java.sql.Timestamp.from(clock.instant()));
        cleanup.clean();
        assertThat(imageStorage.objects).containsKey(key);
        assertThat(jdbc.queryForObject("select count(*) from storage_object_cleanup", Integer.class)).isZero();
        imageStorage.failRead = true;
        var response = imageResponse(id, "PREVIEW", admin);
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("STORAGE_UNAVAILABLE");
    }

    @Test
    void readFailurePreservesCorsAndSecurityHeadersWithRealFilters() throws Exception {
        String admin = administrator();
        var created = multipart("POST", "/admin/spaces", admin, SPACE, image(1), image(2), null);
        assertThat(created.statusCode()).isEqualTo(201);
        String id = body(created).get("id").asString();
        imageStorage.failReadAfterOpen = true;
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/spaces/" + id + "/images/PREVIEW"))
                .header("Authorization", "Bearer " + admin)
                .header("Origin", "https://client.example").GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("STORAGE_UNAVAILABLE");
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains("https://client.example");
        assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(response.headers().firstValue("Content-Security-Policy")).isPresent();
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
    }

    @Test
    void expirationBetweenTwoUploadsDoesNotInstallMissingReferencesOrLoseCompensation() throws Exception {
        String admin = administrator();
        int before = jdbc.queryForObject("select count(*) from space", Integer.class);
        imageStorage.beforeUpload = key -> {
            clock.advance(Duration.ofHours(1));
            cleanup.clean(); // Deletes the due tracking row before the simulated remote upload completes.
        };
        var response = multipart("POST", "/admin/spaces", admin, SPACE, image(1), image(2), null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(500);
        assertThat(jdbc.queryForObject("select count(*) from space", Integer.class)).isEqualTo(before);
        assertThat(imageStorage.objects).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from storage_object_cleanup", Integer.class)).isEqualTo(1);
        clock.advance(Duration.ofHours(1));
        cleanup.clean();
        assertThat(imageStorage.objects).isEmpty();
    }

    @Test
    void delayedFailedUploadRecreatesCompensationInsteadOfLeavingAnUntrackedObject() throws Exception {
        String admin = administrator();
        imageStorage.beforeUpload = key -> {
            clock.advance(Duration.ofHours(1));
            cleanup.clean();
        };
        imageStorage.failUploadAt = 1;
        error(multipart("POST", "/admin/spaces", admin, SPACE, image(1), image(2), null),
                503, "STORAGE_UNAVAILABLE");
        assertThat(imageStorage.objects).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from storage_object_cleanup", Integer.class)).isEqualTo(1);
        clock.advance(Duration.ofHours(1));
        cleanup.clean();
        assertThat(imageStorage.objects).isEmpty();
    }

    @Test
    void preservesAHighResolutionImageOfApproximatelyFifteenMegabytes() throws Exception {
        String admin = administrator();
        var raster = new BufferedImage(3200, 1600, BufferedImage.TYPE_INT_RGB);
        var random = new java.util.Random(17);
        var row = new int[raster.getWidth()];
        for (int y = 0; y < raster.getHeight(); y++) {
            for (int x = 0; x < row.length; x++) row[x] = random.nextInt(0x1000000);
            raster.setRGB(0, y, row.length, 1, row, 0, row.length);
        }
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(raster, "png", bytes);
        raster.flush();
        byte[] original = bytes.toByteArray();
        assertThat(original.length).isBetween(15_000_000, 16_000_000);
        var created = multipart("POST", "/admin/spaces", admin, SPACE, original, image(1), null);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        String id = body(created).get("id").asString();
        assertThat(imageResponse(id, "PREVIEW", admin).body()).containsExactly(original);
        assertThat(jdbc.queryForObject("select i.size_bytes from space_image i "
                + "join space s on s.preview_image_id = i.id where s.id = ?::uuid", Long.class, id))
                .isEqualTo((long) original.length);
    }

    @Test
    void concurrentReplacementsDoNotDeleteTheWinningReference() throws Exception {
        String admin = administrator();
        var created = multipart("POST", "/admin/spaces", admin, SPACE, image(1), image(2), null);
        assertThat(created.statusCode()).isEqualTo(201);
        String id = body(created).get("id").asString();
        byte[] first = image(3);
        byte[] second = image(4);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> multipart("PUT", "/admin/spaces/" + id + "/images/PREVIEW",
                    admin, null, null, null, first));
            var b = executor.submit(() -> multipart("PUT", "/admin/spaces/" + id + "/images/PREVIEW",
                    admin, null, null, null, second));
            assertThat(a.get(15, java.util.concurrent.TimeUnit.SECONDS).statusCode()).isEqualTo(204);
            assertThat(b.get(15, java.util.concurrent.TimeUnit.SECONDS).statusCode()).isEqualTo(204);
        }
        cleanup.clean();
        assertThat(imageStorage.objects).hasSize(2);
        byte[] winner = imageResponse(id, "PREVIEW", admin).body();
        assertThat(java.util.Arrays.equals(winner, first) || java.util.Arrays.equals(winner, second)).isTrue();
        assertThat(imageResponse(id, "PANORAMA", admin).body()).containsExactly(image(2));
    }

    private HttpResponse<byte[]> imageResponse(String id, String kind, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/spaces/" + id + "/images/" + kind));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<String> multipart(String method, String path, String token, String space,
            byte[] preview, byte[] panorama, byte[] replacement) throws Exception {
        var body = new ByteArrayOutputStream();
        if (space != null) part(body, "space", "application/json", space.getBytes(StandardCharsets.UTF_8), false);
        if (preview != null) part(body, "preview", "image/png", preview, true);
        if (panorama != null) part(body, "panorama", "image/png", panorama, true);
        if (replacement != null) part(body, "image", "image/png", replacement, true);
        body.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void part(ByteArrayOutputStream body, String name, String contentType, byte[] content, boolean file)
            throws Exception {
        String filename = file ? "; filename=\"image.png\"" : "";
        body.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"" + name + "\"" + filename
                + "\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private byte[] image(int color) throws Exception {
        var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, color);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }
}
