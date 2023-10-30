/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfStreamsSubscriptionService;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.MdsalRestconfServer;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RestconfDataServiceImpl;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RestconfImpl;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RestconfInvokeOperationsServiceImpl;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RestconfOperationsServiceImpl;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RestconfSchemaServiceImpl;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RestconfStreamsSubscriptionServiceImpl;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.SubscribeToStreamUtil;

@Singleton
public class RestconfApplication extends AbstractRestconfApplication {
    private RestconfApplication(final DatabindProvider databindProvider, final MdsalRestconfServer server,
            final DOMMountPointService mountPointService,
            final RestconfStreamsSubscriptionService streamSubscription, final DOMDataBroker dataBroker,
            final DOMActionService actionService, final DOMNotificationService notificationService,
            final DOMSchemaService domSchemaService, final SubscribeToStreamUtil streamUtils) {
        super(databindProvider, List.of(
            streamSubscription,
            new RestconfDataServiceImpl(databindProvider, server, dataBroker, streamSubscription, actionService),
            new RestconfInvokeOperationsServiceImpl(databindProvider, server, mountPointService, streamUtils),
            new RestconfOperationsServiceImpl(databindProvider, server),
            new RestconfSchemaServiceImpl(domSchemaService, mountPointService),
            new RestconfImpl(databindProvider)));
    }

    @Inject
    public RestconfApplication(final DatabindProvider databindProvider, final MdsalRestconfServer server,
            final DOMMountPointService mountPointService, final DOMDataBroker dataBroker,
            final DOMActionService actionService, final DOMNotificationService notificationService,
            final DOMSchemaService domSchemaService, final SubscribeToStreamUtil streamUtils) {
        this(databindProvider, server, mountPointService,
            new RestconfStreamsSubscriptionServiceImpl(dataBroker, notificationService, databindProvider, streamUtils),
            dataBroker, actionService, notificationService, domSchemaService, streamUtils);
    }
}
