package org.example.common.mongo.converter;

import org.example.common.time.ServiceTimeConverter;
import org.springframework.core.convert.converter.Converter;

import java.time.LocalDateTime;
import java.util.Date;

public class DateToLocalDateTimeConverter implements Converter<Date, LocalDateTime> {

    @Override
    public LocalDateTime convert(Date source) {
        return ServiceTimeConverter.toLocalDateTime(source.toInstant());
    }
}
