/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.config;

import java.util.concurrent.ThreadFactory;
import org.opendaylight.controller.config.threadpool.util.NamingThreadPoolFactory;

/**
 * A {@link NamingThreadPoolFactory} configured with a {@code namePrefix}, except in disguise.
 */
public sealed interface GlobalNetconfThreadFactory extends ThreadFactory permits GlobalNetconfThreadFactoryFactory {
    // Just a marker interface
}
