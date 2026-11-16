package unimagdeportes.service.space;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import unimagdeportes.exception.BusinessException;

class SpaceImageValidatorTest {
    private final SpaceImageValidator validator = new SpaceImageValidator();

    @Test
    void detectsContentRatherThanTrustingDeclaredType() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", output);
        var file = new MockMultipartFile("image", "image.txt", "text/plain", output.toByteArray());

        var image = validator.read(file);

        assertThat(image.getContentType()).isEqualTo("image/png");
        assertThat(image.getContent()).containsExactly(output.toByteArray());
    }

    @Test
    void rejectsMissingInvalidAndOversizedContent() {
        assertThatThrownBy(() -> validator.read(null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validator.read(new MockMultipartFile("image", new byte[]{1, 2, 3})))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validator.read(new MockMultipartFile("image",
                new byte[(int) SpaceImageValidator.MAX_IMAGE_BYTES + 1]))).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsTruncatedImageWithRecognizableHeader() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", output);
        byte[] truncated = Arrays.copyOf(output.toByteArray(), 45);

        assertThatThrownBy(() -> validator.read(new MockMultipartFile("image", truncated)))
                .isInstanceOf(BusinessException.class);
    }
}
