/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.ErrorMessage;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.server.api.DatabindPath.Data;
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
 * @param appTag value {@code error-api-tag} leaf
 * @param info optional content of {@code error-info} anydata
 */
@NonNullByDefault
public record ServerError(
        ErrorType type,
        ErrorTag tag,
        @Nullable ErrorMessage message,
        @Nullable String appTag,
        @Nullable Data path,
        @Nullable FormattableBody info) {
    public ServerError {
        requireNonNull(type);
        requireNonNull(tag);
    }
}
