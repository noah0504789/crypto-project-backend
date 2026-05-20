package org.example.dlq.application;

import lombok.RequiredArgsConstructor;
import org.example.common.exception.DlqNotFoundException;
import org.example.dlq.Dlq;
import org.example.dlq.adapter.DlqRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DlqService {

    private final DlqRepository dlqRepository;

    @Transactional("transactionManager")
    public void save(Dlq dlq) {
        dlqRepository.save(dlq);
    }

    @Transactional("transactionManager")
    public void saveAll(List<Dlq> dlqList) {
        dlqRepository.saveAll(dlqList);
    }

    @Transactional("transactionManager")
    public void complete(String id) {
        Dlq dlq = dlqRepository.findById(id)
                .orElseThrow(() -> new DlqNotFoundException(id));

        dlq.markCompleted();
    }

    @Transactional("transactionManager")
    public void fail(String id, String errorMessage) {
        Dlq dlq = dlqRepository.findById(id)
                .orElseThrow(() -> new DlqNotFoundException(id));

        dlq.markFailed(errorMessage);
    }
}
