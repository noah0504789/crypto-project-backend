package org.example.user.account.domain.exception;

import org.example.common.exception.ForbiddenException;

import java.util.UUID;

public class UserAccessDeniedException extends ForbiddenException {
    public UserAccessDeniedException(UUID publicId, UUID actorPublicId) {
        super(String.format("Not the owner of publicId=%s, actorPublicId=%s", publicId, actorPublicId));
    }
}
