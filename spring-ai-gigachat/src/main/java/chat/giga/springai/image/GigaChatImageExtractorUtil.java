package chat.giga.springai.image;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GigaChatImageExtractorUtil {
    private static final Pattern IMG_ID_PATTERN = Pattern.compile(
            "<img\\s+src=\"([a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12})\"");

    private GigaChatImageExtractorUtil() {}

    /**
     * Извлекает уникальные идентификаторы изображений (UUID) из переданного текстового контента.
     * <p>
     * Метод ищет в тексте HTML-теги {@code <img>} с атрибутом {@code src},
     * значением которого является 36-символьный UUID (например, в формате GigaChat API).
     * </p>
     *
     * @param content исходный текст или HTML-код для анализа; может быть {@code null}
     * @return {@link List} строк, содержащий все найденные идентификаторы файлов;
     *         возвращает пустую коллекцию, если совпадений не найдено или {@code content} равен {@code null}
     */
    public static List<String> extract(String content) {
        List<String> fileIds = new ArrayList<>();

        if (content != null) {
            Matcher matcher = IMG_ID_PATTERN.matcher(content);

            while (matcher.find()) {
                String fileId = matcher.group(1);
                fileIds.add(fileId);
            }
        }

        return fileIds;
    }
}
