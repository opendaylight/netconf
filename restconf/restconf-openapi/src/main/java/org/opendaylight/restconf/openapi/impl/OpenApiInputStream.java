/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.opendaylight.restconf.openapi.model.Info;
import org.opendaylight.restconf.openapi.model.Server;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public class OpenApiInputStream extends InputStream implements Iterable<Integer> {
    private final EffectiveModelContext context;
    private final String openApiVersion;
    private final Info info;
    private final List<Server> servers;
    private final List<Map<String, List<String>>> security;

    public OpenApiInputStream(final EffectiveModelContext context, final String openApiVersion, final Info info,
            final List<Server> servers, final List<Map<String, List<String>>> security) {
        this.context = context;
        this.openApiVersion = openApiVersion;
        this.info = info;
        this.servers = servers;
        this.security = security;
    }

    @Override
    public Iterator<Integer> iterator() {
        return new OpenApiModelIterator(context, openApiVersion, info, servers, security);
    }

    @Override
    public int read() throws IOException {
        return iterator().next();
    }
}
