/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yanglib.impl;

import static java.util.Objects.requireNonNull;

import java.util.Set;
import javax.ws.rs.core.Application;
import org.opendaylight.yanglib.api.YangLibService;

public final class YangLibRestApp extends Application {
    private final YangLibService yangLibService;

    public YangLibRestApp(final YangLibService yangLibService) {
        this.yangLibService = requireNonNull(yangLibService);
    }

    @Override
    public Set<Object> getSingletons() {
        return Set.of(yangLibService);
    }
}
