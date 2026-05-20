package org.example.common.mongo;

import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.model.FieldNamingStrategy;

public class SnakeCaseFieldNamingStrategy implements FieldNamingStrategy {
    @Override
    public String getFieldName(PersistentProperty<?> property) {
        String name = property.getName();

        return camelToSnake(name);
    }

    private String camelToSnake(String str) {
        StringBuffer sb = new StringBuffer();
        char[] charArray = str.toCharArray();

        for (char c : charArray) {
            if (Character.isUpperCase(c)) {
                sb.append("_").append(Character.toLowerCase(c));
                continue;
            }

            sb.append(c);
        }

        return sb.toString();
    }
}
