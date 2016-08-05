/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.data.reader;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.SettableFuture;
import java.util.Collection;
import java.util.concurrent.Future;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.ClusteredDOMDataTreeChangeListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataTreeChangeListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataTreeChangeService;
import org.opendaylight.controller.md.sal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.tree.DataTreeCandidate;

/**
 * http://stackoverflow.com/questions/6840803/simpledateformat-thread-safety
 */

public class ListenerReader implements ClusteredDOMDataTreeChangeListener {
    private ThreadLocal<SettableFuture<Optional<NormalizedNode<?, ?>>>> localFuture;
    private ListenerRegistration<DOMDataTreeChangeListener> registration;

    public Future<Optional<NormalizedNode<?, ?>>> readNode(final YangInstanceIdentifier path,
                                                           final DOMDataBroker broker,
                                                           final LogicalDatastoreType dataStore) {
        this.localFuture = ThreadLocal.withInitial(SettableFuture::create);

        final DOMDataTreeChangeService service = (DOMDataTreeChangeService) broker.getSupportedExtensions()
                .get(DOMDataTreeChangeService.class);
        final DOMDataTreeIdentifier identifier = new DOMDataTreeIdentifier(dataStore, path);
        this.registration = service.registerDataTreeChangeListener(identifier, this);
        return this.localFuture.get();
    }

    @Override
    public void onDataTreeChanged(@Nonnull final Collection<DataTreeCandidate> collection) {
        collection.parallelStream().forEachOrdered(s -> this.localFuture.get().set(s.getRootNode().getDataAfter()));
        this.registration.close();
    }
}
