/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.api;

import static java.util.Objects.requireNonNull;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.text.ParseException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serial form of {@link ApiPath}.
 */
@NonNullByDefault
record APv1(String path) implements Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(APv1.class);
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    APv1 {
        requireNonNull(path);
    }

    @java.io.Serial
    @SuppressWarnings("checkstyle:avoidHidingCauseException")
    private Object readResolve() throws ObjectStreamException {
        try {
            return ApiPath.parse(path);
        } catch (ParseException e) {
            LOG.debug("Invalid path '{}'", path, e);
            throw new StreamCorruptedException(e.getMessage());
        }
    }
}
