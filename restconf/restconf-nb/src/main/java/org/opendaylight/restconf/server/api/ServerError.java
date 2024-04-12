/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.ErrorMessage;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

/**
 * Encapsulates a single {@code error} within the
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.9">"errors" YANG Data Template</a> as bound to a particular
 * {@link DatabindContext}.
 *
 * @param type value of {@code error-type} leaf
 * @param tag value of {@code error-tag} leaf
 * @param message value of {@code error-message} leaf, potentially with metadata
 * @param appTag value of {@code error-api-tag} leaf
 * @param path optional {@code error-path} leaf
 * @param info optional content of {@code error-info} anydata
 */
@NonNullByDefault
public record ServerError(
        ErrorType type,
        ErrorTag tag,
        @Nullable ErrorMessage message,
        @Nullable String appTag,
        @Nullable ServerErrorPath path,
        @Nullable ServerErrorInfo info) {
    public ServerError {
        requireNonNull(type);
        requireNonNull(tag);
    }

    public ServerError(final ErrorType type, final ErrorTag tag, final String message) {
        this(type, tag, new ErrorMessage(message), null, null, null);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).omitNullValues()
            .add("type", type)
            .add("tag", tag)
            .add("appTag", appTag)
            .add("message", message)
            .add("path", path)
            .add("info", info)
            .toString();
    }
}
