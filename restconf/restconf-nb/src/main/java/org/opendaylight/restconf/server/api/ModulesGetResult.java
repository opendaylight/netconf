/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import com.google.common.io.CharSource;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Result of an {@link RestconfServer#modulesYangGET(ServerRequest, String, String)} invocation.
 *
 * @param source A {@link CharSource} containing the body
 */
@NonNullByDefault
public record ModulesGetResult(CharSource source) {
    public ModulesGetResult {
        requireNonNull(source);
    }
}
