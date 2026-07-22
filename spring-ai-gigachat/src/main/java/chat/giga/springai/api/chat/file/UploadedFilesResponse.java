package chat.giga.springai.api.chat.file;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Ответ GET /files — массив метаданных доступных файлов.
 * Соответствует модели {@code UploadedFiles} в SDK ai-forever/gigachat.
 */
public record UploadedFilesResponse(@JsonProperty("data") List<UploadFileResponse> data) {}
