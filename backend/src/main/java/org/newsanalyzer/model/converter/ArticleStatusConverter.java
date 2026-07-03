package org.newsanalyzer.model.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.newsanalyzer.model.ArticleStatus;

/**
 * JPA converter for ArticleStatus enum.
 * Converts between database values (lowercase) and Java enum constants.
 */
@Converter(autoApply = true)
public class ArticleStatusConverter implements AttributeConverter<ArticleStatus, String> {

    @Override
    public String convertToDatabaseColumn(ArticleStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getValue();
    }

    @Override
    public ArticleStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        return ArticleStatus.fromValue(dbData);
    }
}
