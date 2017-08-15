/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl;

import com.google.common.util.concurrent.SettableFuture;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.ClusteredDOMDataTreeChangeListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataTreeChangeService;
import org.opendaylight.controller.md.sal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.tree.DataTreeCandidate;

public class DTChangeListenerImpl implements ClusteredDOMDataTreeChangeListener {

    private Collection<DataTreeCandidate> changes;
    private final DOMDataBroker domDataBroker;
    private final LogicalDatastoreType datastoreType;
    private final YangInstanceIdentifier path;
    private ListenerRegistration<DTChangeListenerImpl> registerDataTreeChangeListener;
    private final SettableFuture<Collection<DataTreeCandidate>> future;

    public DTChangeListenerImpl(final DOMDataBroker domDataBroker, final LogicalDatastoreType datastoreType,
            final YangInstanceIdentifier path) {
        this.domDataBroker = domDataBroker;
        this.datastoreType = datastoreType;
        this.path = path;
        future = SettableFuture.create();
    }


    @Override
    public void onDataTreeChanged(final Collection<DataTreeCandidate> changes) {
        future.set(changes);
        registerDataTreeChangeListener.close();
    }

    public Collection<DataTreeCandidate> readData() {
        final DOMDataTreeChangeService dataTreeChangeService =
                (DOMDataTreeChangeService) domDataBroker.getSupportedExtensions().get(DOMDataTreeChangeService.class);

        final DOMDataTreeIdentifier treeId = new DOMDataTreeIdentifier(datastoreType, path);
        registerDataTreeChangeListener = dataTreeChangeService.registerDataTreeChangeListener(treeId, this);
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RestconfDocumentedException("Problem while getting data");
        }
    }



}
