/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

/**
 * Auxiliary interface for instantiating JAX-RS streams.
 *
 * @deprecated This componet exists only to support SSE/Websocket delivery. It will be removed when support for
 *             WebSockets is removed.
 */
@Component(factory = DefaultRestconfStreamServletFactory.FACTORY_NAME, service = RestconfStreamServletFactory.class)
@Deprecated(since = "7.0.0", forRemoval = true)
public final class DefaultRestconfStreamServletFactory implements RestconfStreamServletFactory {
    public static final String FACTORY_NAME =
        "org.opendaylight.restconf.nb.rfc8040.streams.RestconfStreamServletFactory";

    private static final String PROP_RESTCONF = ".restconf";

    private final @NonNull String restconf;

    public DefaultRestconfStreamServletFactory(final String restconf) {
        this.restconf = requireNonNull(restconf);
        if (restconf.endsWith("/")) {
            throw new IllegalArgumentException("{+restconf} value ends with /");
        }
    }

    @Activate
    public DefaultRestconfStreamServletFactory(final Map<String, ?> props) {
        this((String) props.get(PROP_RESTCONF));
    }

    @Override
    public String restconf() {
        return restconf;
    }

    public static Map<String, ?> props(final String restconf) {
        return Map.of(PROP_RESTCONF, restconf);
    }
}
