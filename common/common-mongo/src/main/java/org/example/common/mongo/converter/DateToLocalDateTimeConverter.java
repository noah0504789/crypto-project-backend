package org.example.common.mongo.converter;

import org.example.common.time.ServiceZoneUtils;
import org.springframework.core.convert.converter.Converter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

public class DateToLocalDateTimeConverter implements Converter<Date, LocalDateTime> {

    @Override
    public LocalDateTime convert(Date source) {
        return LocalDateTime.ofInstant(source.toInstant(), ServiceZoneUtils.ZONE_ID);
    }
}
