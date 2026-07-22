package com.penmate.backend.application.iam;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.novel.NovelTrashApplicationService;
import com.penmate.backend.domain.iam.repository.IamGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AccountPurgeService {
    private final NovelTrashApplicationService novelTrash;
    private final IamGateway iam;

    public AccountPurgeService(NovelTrashApplicationService novelTrash, IamGateway iam) {
        this.novelTrash = novelTrash;
        this.iam = iam;
    }

    @Transactional
    public void purge(Long userId, Instant now) {
        novelTrash.purgeAllProjectsForAccount(userId);
        if (iam.purgePendingUserDeletion(userId, now) != 1) {
            throw BusinessException.conflict("Account is no longer eligible for deletion");
        }
    }
}
