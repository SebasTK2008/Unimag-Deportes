package unimagdeportes.integration.space;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import unimagdeportes.support.RulesTestSupport;

class SpaceImageIntegrationTest extends RulesTestSupport {
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
        assertThat(send("GET", "/admin/spaces", null, admin).body()).doesNotContain("content", "previewImage");
        assertThat(imageResponse(id, "PREVIEW", user).body()).containsExactly(preview);
        assertThat(imageResponse(id, "PANORAMA", user).body()).containsExactly(panorama);
        assertThat(imageResponse(id, "PREVIEW", null).statusCode()).isEqualTo(401);

        var forbidden = multipart("PUT", "/admin/spaces/" + id + "/images/PREVIEW",
                user, null, null, null, replacement);
        assertThat(forbidden.statusCode()).isEqualTo(403);
        var updated = multipart("PUT", "/admin/spaces/" + id + "/images/PREVIEW",
                admin, null, null, null, replacement);
        assertThat(updated.statusCode()).as(updated.body()).isEqualTo(204);
        assertThat(imageResponse(id, "PREVIEW", user).body()).containsExactly(replacement);
        assertThat(imageResponse(id, "PANORAMA", user).body()).containsExactly(panorama);
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
