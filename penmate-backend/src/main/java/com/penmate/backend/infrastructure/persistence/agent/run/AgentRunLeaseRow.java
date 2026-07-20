package com.penmate.backend.infrastructure.persistence.agent.run;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class AgentRunLeaseRow {

    private Long runId;
    private String leaseOwner;
    private Long executionToken;
    private Integer attemptCount;
    private Instant leaseUntil;
    private String acquiredFrom;
}
