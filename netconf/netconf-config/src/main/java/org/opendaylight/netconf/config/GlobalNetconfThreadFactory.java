/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.config;

import javax.inject.Inject;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.controller.config.threadpool.util.NamingThreadPoolFactory;

/**
 * Shared {@link NamingThreadPoolFactory} for {@link GlobalNetconfProcessingExecutor}.
 */
@NonNullByDefault
public final class GlobalNetconfThreadFactory extends NamingThreadPoolFactory {
    public static final String DEFAULT_NAME_PREFIX = "remote-connector-processing-executor";

    public GlobalNetconfThreadFactory(final String namePrefix) {
        super(namePrefix);
    }

    @Inject
    public GlobalNetconfThreadFactory() {
        this(DEFAULT_NAME_PREFIX);
    }
}
