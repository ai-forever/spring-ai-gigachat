package chat.giga.springai.api.chat.file;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record UploadFileResponse(
        // Размер файла в байтах
        Integer bytes,

        // Время создания файла в формате unix timestamp.
        @JsonProperty("created_at") Long createdAt,

        // Имя файла
        String filename,

        // Идентификатор файла. Добавляется к сообщению пользователя для работы с файлом
        UUID id,

        // Тип объекта. Всегда равен file.
        String object,

        /*
         * Назначение файла
         * Возможные значения: general
         * */
        String purpose,

        /*
         * Доступность файла
         * Возможные значения: public, private
         * */
        @JsonProperty("access_policy") String accessPolicy) {}
