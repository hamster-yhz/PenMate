package com.penmate.backend.infrastructure.scheduling;

import com.penmate.backend.application.ops.AsyncJobWorker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OpsJobScheduler {

    private final AsyncJobWorker worker;

    public OpsJobScheduler(AsyncJobWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${penmate.jobs.poll-delay:PT1S}")
    public void poll() {
        worker.poll();
    }
}
