/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.aries.blueprint.annotation.service.Reference;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.services.wrapper.ServicesWrapper;

@Singleton
public class RestconfApplication extends AbstractRestconfApplication<ServicesWrapper> {
    @Inject
    public RestconfApplication(final SchemaContextHandler schemaContextHandler,
            @Reference final DOMMountPointService mountPointService, final ServicesWrapper servicesNotifWrapper) {
        super(schemaContextHandler, mountPointService, servicesNotifWrapper);
    }
}
