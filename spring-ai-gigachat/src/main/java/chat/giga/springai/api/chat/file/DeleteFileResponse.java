package chat.giga.springai.api.chat.file;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record DeleteFileResponse(
        // Идентификатор файла.
        UUID id,

        // Признак удаления файла.
        boolean deleted,

        /*
         * Доступность файла
         * Возможные значения: public, private
         * */
        @JsonProperty("access_policy") String accessPolicy) {}
