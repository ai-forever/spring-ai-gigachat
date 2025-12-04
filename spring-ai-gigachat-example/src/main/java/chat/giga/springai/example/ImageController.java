package chat.giga.springai.example;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import chat.giga.springai.image.GigaChatImageOptions;
import java.util.Base64;
import java.util.Map;
import org.springframework.ai.image.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/image", produces = APPLICATION_JSON_VALUE)
public class ImageController {

    private final ImageModel imageModel;

    public ImageController(ImageModel imageModel) {
        this.imageModel = imageModel;
    }

    @PostMapping(value = "/generate", consumes = APPLICATION_JSON_VALUE)
    public Map<String, Object> generate(@RequestBody String request) {
        ImagePrompt prompt = new ImagePrompt(request);
        ImageResponse response = imageModel.call(prompt);
        ImageGeneration result = response.getResult();
        String base64 = result.getOutput().getB64Json();

        return Map.of("prompt", request, "base64", base64);
    }

    @PostMapping(value = "/raw", produces = MediaType.IMAGE_JPEG_VALUE)
    public byte[] generateRaw(@RequestBody String request) {
        ImagePrompt prompt = new ImagePrompt(
                request, GigaChatImageOptions.builder().model("GigaChat-2-Max").build());
        ImageResponse response = imageModel.call(prompt);
        String b64 = response.getResult().getOutput().getB64Json();

        return Base64.getDecoder().decode(b64);
    }
}
