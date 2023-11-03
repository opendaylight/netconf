/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;

/**
 * Abstract class for processing and preparing data.
 */
abstract class AbstractNotificationsData {
    protected DatabindProvider databindProvider;
    private DOMDataBroker dataBroker;

    /**
     * Data broker for delete data in DS on close().
     *
     * @param dataBroker creating new write transaction for delete data on close
     * @param databindProvider for formatting notifications
     */
    @SuppressWarnings("checkstyle:hiddenField")
    // FIXME: this is pure lifecycle nightmare just because ...
    public void setCloseVars(final DOMDataBroker dataBroker, final DatabindProvider databindProvider) {
        this.dataBroker = dataBroker;
        this.databindProvider = databindProvider;
    }

    /**
     * Delete data in DS.
     */
    // FIXME: here we touch datastore, which probably should be done by whoever instantiated us or created the resource,
    //        or they should be giving us the transaction
    protected final ListenableFuture<?> deleteDataInDS(final String streamName) {
        final var wTx = dataBroker.newWriteOnlyTransaction();
        wTx.delete(LogicalDatastoreType.OPERATIONAL, RestconfStateStreams.restconfStateStreamPath(streamName));
        return wTx.commit();
    }
}
