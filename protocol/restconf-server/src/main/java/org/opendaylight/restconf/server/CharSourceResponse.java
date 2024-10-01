/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import com.google.common.io.CharSource;
import io.netty.util.AsciiString;
import org.eclipse.jdt.annotation.NonNull;

/**
 * A {@link Response} containing a {@link CharSource} with some accompanying headers.
 */
record CharSourceResponse(@NonNull CharSource source, AsciiString mediaType) implements Response {
    CharSourceResponse {
        requireNonNull(source);
        requireNonNull(mediaType);
    }
}
