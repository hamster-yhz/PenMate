package com.penmate.backend.application.support;

import com.penmate.backend.domain.shared.service.AuditService;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public abstract class BaseApplicationServiceTest {

    @Mock
    protected AuditService auditService;
}