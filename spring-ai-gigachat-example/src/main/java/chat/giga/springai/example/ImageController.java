package chat.giga.springai.example;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.ai.image.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/image", produces = APPLICATION_JSON_VALUE)
public class ImageController {

    private final ImageModel imageModel;

    public ImageController(ImageModel imageModel) {
        this.imageModel = imageModel;
    }

    @PostMapping(value = "/generate", consumes = APPLICATION_JSON_VALUE)
    public Map<String, Object> generate(@RequestBody GenerateImageRequest request) {
        ImagePrompt prompt = new ImagePrompt(
                List.of(
                        new ImageMessage(request.prompt(), 1.0f)
                ),
                ImageOptionsBuilder.builder().build()
        );
        ImageResponse response = imageModel.call(prompt);
        ImageGeneration result = response.getResult();
        String base64 = result.getOutput().getB64Json();

        return Map.of(
                "prompt", request.prompt(),
                "base64", base64
        );
    }

    @PostMapping(value = "/raw", produces = MediaType.IMAGE_JPEG_VALUE)
    public byte[] generateRaw(@RequestBody GenerateImageRequest request) {
        ImagePrompt pr = new ImagePrompt(
                List.of(new ImageMessage(request.prompt(), 1.0f)),
                ImageOptionsBuilder.builder().build()
        );
        ImageResponse resp = imageModel.call(pr);

        String b64 = resp.getResult().getOutput().getB64Json();

        return Base64.getDecoder().decode(b64);
    }

    public record GenerateImageRequest(String prompt) {}

}
