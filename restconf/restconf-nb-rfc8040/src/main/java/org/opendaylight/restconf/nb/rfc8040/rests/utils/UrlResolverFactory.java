/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import org.opendaylight.restconf.nb.rfc8040.streams.Configuration;

/**
 * Factory which create proper {@link UrlResolver} from {@link Configuration}
 *
 */
public class UrlResolverFactory {

    private final boolean useSSE;

    public UrlResolverFactory(Configuration conf) {
        this.useSSE = conf.isUseSSE();
    }

    public UrlResolver build() {
        if (useSSE) {
            return new UrlResolverSSE();
        } else {
            return new UrlResolverWS();
        }
    }

    public static UrlResolver getFactory(Configuration conf) {
        return new UrlResolverFactory(conf).build();
    }
}
