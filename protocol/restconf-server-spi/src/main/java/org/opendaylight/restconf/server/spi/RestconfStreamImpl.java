/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

public final class RestconfStreamImpl<T> extends RestconfStream<T> {
    public RestconfStreamImpl(AbstractRestconfStreamRegistry registry, Source source, String name) {
        super(registry, source, name);
    }

    @Override
    void onLastSubscriber() {
        //no-op
    }
}
