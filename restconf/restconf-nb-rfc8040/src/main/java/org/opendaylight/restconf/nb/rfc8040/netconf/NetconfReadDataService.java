/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.netconf;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.CompletionStage;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.nb.rfc8040.ApiPath;
import org.opendaylight.restconf.nb.rfc8040.ReadDataParams;
import org.opendaylight.restconf.nb.rfc8040.api.ReadDataResponse;
import org.opendaylight.restconf.nb.rfc8040.api.ReadDataService;

/**
 * {@link ReadDataService} implemented on top of {@link DOMDataTreeReadTransaction}.
 */
// TODO: this should live in in a separate artifact and be wired through netconf-topology (or whoever binds to
//       DOMMountPointService).
public final class NetconfReadDataService implements ReadDataService {
    private final NetconfDataTreeService netconfDataTree;
    // FIXME: hmm... NETCONF should be able to work without much in terms of schema, as we just need namespace mapping
    //        and NETCONF does know about module names, hence the service itself should be able to do that. Perhaps we
    //        need an equivalent of a ReadOnlyTransaction, which would expose its bound context (i.e. constant).
    private final DOMSchemaService schema;

    public NetconfReadDataService(final DOMSchemaService schema, final NetconfDataTreeService netconfDataTree) {
        this.schema = requireNonNull(schema);
        this.netconfDataTree = requireNonNull(netconfDataTree);
    }

    @Override
    public CompletionStage<ReadDataResponse> readData(final ApiPath path, final ReadDataParams parameters) {
        // FIXME: implement this
        //    node = ReadDataTransactionUtil.readData(parameters.content(), instanceIdentifier.getInstanceIdentifier(),
        //        strategy, parameters.withDefaults(), schemaContextRef, fieldPaths);
        throw new UnsupportedOperationException();
    }
}
