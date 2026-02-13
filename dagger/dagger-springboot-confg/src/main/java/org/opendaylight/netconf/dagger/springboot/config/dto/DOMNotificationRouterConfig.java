/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.springboot.config.dto;

import java.lang.annotation.Annotation;
import org.opendaylight.mdsal.dom.broker.DOMNotificationRouter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;

/**
 * Implementation of OSGi DOMNotificationRouter configuration used for components that are not initialized
 * or managed by the OSGi.
 */
@ConfigurationProperties
public class DOMNotificationRouterConfig implements DOMNotificationRouter.Config {
    private int queueDepth = 65536;

    @ConstructorBinding
    public DOMNotificationRouterConfig(@Name("notification-queue-depth") @DefaultValue("65536") int queueDepth) {
        this.queueDepth = queueDepth;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return DOMNotificationRouter.Config.class;
    }

    @Override
    public int queueDepth() {
        return queueDepth;
    }
}
