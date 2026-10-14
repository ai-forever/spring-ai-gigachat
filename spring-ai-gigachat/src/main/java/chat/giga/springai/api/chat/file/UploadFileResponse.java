/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
