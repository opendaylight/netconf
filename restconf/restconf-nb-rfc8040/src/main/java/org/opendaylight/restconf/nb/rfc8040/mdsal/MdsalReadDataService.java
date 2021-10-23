/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.mdsal;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.CompletionStage;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.nb.rfc8040.ApiPath;
import org.opendaylight.restconf.nb.rfc8040.ReadDataParams;
import org.opendaylight.restconf.nb.rfc8040.api.ReadDataResponse;
import org.opendaylight.restconf.nb.rfc8040.api.ReadDataService;

/**
 * {@link ReadDataService} implemented on top of {@link DOMDataTreeReadTransaction}.
 */
public final class MdsalReadDataService implements ReadDataService {
    private final DOMMountPointService mountPoint;
    private final DOMDataBroker dataBroker;
    private final DOMSchemaService schema;

    public MdsalReadDataService(final DOMSchemaService schema, final DOMDataBroker dataBroker,
            final DOMMountPointService mountPoint) {
        this.schema = requireNonNull(schema);
        this.dataBroker = requireNonNull(dataBroker);
        this.mountPoint = requireNonNull(mountPoint);
    }

    @Override
    public CompletionStage<ReadDataResponse> readData(final ApiPath path, final ReadDataParams parameters) {
        // FIXME: determine whether this is a local apiPath or not
        //        - if it is not, talk to mountPoint service and forward the request
        //        - if it is, binding to current schema, allocate a read transaction and execute it on top of it
        throw new UnsupportedOperationException();
    }
}
