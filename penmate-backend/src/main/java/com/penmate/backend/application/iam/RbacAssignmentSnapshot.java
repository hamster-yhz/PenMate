package com.penmate.backend.application.iam;

import java.util.List;

/** Immutable relationship snapshot used by the RBAC API concurrency contract. */
public record RbacAssignmentSnapshot<T>(Long revision, List<T> items) {
}
