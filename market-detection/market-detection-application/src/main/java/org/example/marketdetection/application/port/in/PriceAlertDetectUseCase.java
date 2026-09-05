package org.example.marketdetection.application.port.in;

import java.util.List;
import org.example.marketdetection.contract.event.PriceAlertDetectedEvent;
import org.example.marketdetection.application.dto.PricePoint;

public interface PriceAlertDetectUseCase {

    List<PriceAlertDetectedEvent> detect(String code, PricePoint pricePoint, List<PricePoint> recentPoints);

    boolean isStale(long staleCheckMs);
}
