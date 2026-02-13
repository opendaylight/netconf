/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.smallrye.config.dto;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import org.opendaylight.mdsal.dom.broker.DOMNotificationRouter;

/**
 * Implementation of OSGi DOMNotificationRouter configuration used for components that are not initialized
 * or managed by the OSGi.
 */
@ConfigMapping(prefix = "")
public interface DOMNotificationRouterConfig extends DOMNotificationRouter.Config {

    @WithName("notification-queue-depth")
    @WithDefault("65536")
    int queueDepth();
}
