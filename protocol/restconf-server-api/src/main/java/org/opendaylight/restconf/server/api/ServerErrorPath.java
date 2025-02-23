/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import org.opendaylight.netconf.common.DatabindContext;
import org.opendaylight.restconf.server.api.DatabindPath.Data;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

/**
 * An {@code error-path} element in a {@link ServerError}.
 *
 * @param databind the {@link DatabindContext} to which this path is bound
 * @param path the {@link YangInstanceIdentifier}, {@link YangInstanceIdentifier#empty()} denotes the data root
 */
public record ServerErrorPath(DatabindContext databind, YangInstanceIdentifier path) {
    public ServerErrorPath {
        requireNonNull(databind);
        requireNonNull(path);
    }

    public ServerErrorPath(final Data path) {
        this(path.databind(), path.instance());
    }
}