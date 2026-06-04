package org.example.common.time;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.ZoneId;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ServiceZoneUtils {

    public static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");
}
