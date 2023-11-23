/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import java.io.InputStream;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Result of an {@link RestconfServer#modulesGET(String)} invocation.
 */
@NonNullByDefault
public record ModulesGetResult(InputStream stream) {
    public ModulesGetResult {
        requireNonNull(stream);
    }
}
