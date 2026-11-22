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
    private final SpaceImageValidator validator = new SpaceImageValidator(64_000_000, 16384);

    @Test
    void detectsContentRatherThanTrustingDeclaredType() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", output);
        var file = new MockMultipartFile("image", "image.txt", "text/plain", output.toByteArray());

        var type = validator.validate(file);

        assertThat(type).isEqualTo("image/png");
    }

    @Test
    void rejectsMissingInvalidAndOversizedContent() {
        assertThatThrownBy(() -> validator.validate(null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile("image", new byte[]{1, 2, 3})))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile("image",
                new byte[(int) SpaceImageValidator.MAX_IMAGE_BYTES + 1]))).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsTruncatedImageWithRecognizableHeader() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", output);
        byte[] truncated = Arrays.copyOf(output.toByteArray(), 45);

        assertThatThrownBy(() -> validator.validate(new MockMultipartFile("image", truncated)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void boundsPixelsBeforeDecodingAndDoesNotReadAWholeByteArray() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", output);
        var file = new MockMultipartFile("image", output.toByteArray()) {
            @Override public byte[] getBytes() { throw new AssertionError("No bulk read"); }
        };
        assertThat(validator.validate(file)).isEqualTo("image/png");
        assertThatThrownBy(() -> new SpaceImageValidator(99, 16384).validate(file))
                .isInstanceOf(BusinessException.class).hasMessageContaining("píxeles");
        assertThatThrownBy(() -> new SpaceImageValidator(0, 16384)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SpaceImageValidator(64_000_000, 9).validate(file))
                .isInstanceOf(BusinessException.class);
    }
}
