package org.example.outboxpoller.dlq;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${api-path.dlq-poller.base:/dlq-poller}")
@RequiredArgsConstructor
public class DlqPollerController {

    private final DlqPollerState dlqPollerState;

    @PutMapping("${api-path.dlq-poller.start:/start}")
    public String start() {
        dlqPollerState.start();
        return "dlq poller start";
    }

    @PutMapping("${api-path.dlq-poller.stop:/stop}")
    public String stop() {
        dlqPollerState.stop();
        return "dlq poller stop";
    }
}
