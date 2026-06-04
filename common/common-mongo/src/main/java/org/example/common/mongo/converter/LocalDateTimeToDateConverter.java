package org.example.common.mongo.converter;

import org.example.common.time.ServiceZoneUtils;
import org.springframework.core.convert.converter.Converter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

public class LocalDateTimeToDateConverter implements Converter<LocalDateTime, Date> {

    @Override
    public Date convert(LocalDateTime source) {
        return Date.from(source.atZone(ServiceZoneUtils.ZONE_ID).toInstant());
    }
}
