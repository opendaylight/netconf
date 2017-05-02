/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.handlers;

import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;

public class NotificationServiceHandler implements Handler<DOMNotificationService> {

    private final DOMNotificationService notificationService;

    /**
     * Set DOMNotificationService.
     *
     * @param notificationService
     *             DOMNotificationService
     */
    public NotificationServiceHandler(final DOMNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public DOMNotificationService get() {
        return this.notificationService;
    }

}
