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

public class ListenerReader implements DOMDataTreeChangeListener {
    private ThreadLocal<ListenerRegistration<ClusteredDOMDataTreeChangeListener>> registration;
    private ThreadLocal<SettableFuture<Optional<NormalizedNode<?, ?>>>> localFuture;
    private ListenerRegistration<ListenerReader> registerDataTreeChangeListener;

    public Future<Optional<NormalizedNode<?, ?>>> readNode(final YangInstanceIdentifier path,
            final DOMDataBroker broker, final LogicalDatastoreType datastoreType) {
        this.localFuture = new ThreadLocal<SettableFuture<Optional<NormalizedNode<?, ?>>>>() {
            @Override
            protected SettableFuture<Optional<NormalizedNode<?, ?>>> initialValue() {
                return SettableFuture.create();
            }
        };

        // TODO : add registration
        final DOMDataTreeChangeService service = (DOMDataTreeChangeService) broker
                .getSupportedExtensions().get(DOMDataTreeChangeService.class);
        final DOMDataTreeIdentifier identi = new DOMDataTreeIdentifier(datastoreType, path);
        this.registerDataTreeChangeListener = service.registerDataTreeChangeListener(identi, this);
        return this.localFuture.get();
    }

    @Override
    public void onDataTreeChanged(@Nonnull final Collection<DataTreeCandidate> collection) {
        for (final DataTreeCandidate child : collection) {
            final YangInstanceIdentifier rootPath = child.getRootPath();
            System.out.println(rootPath.toString());
        }
        this.localFuture.get().set(null);
        // this.localFuture.set(Optional.fromNullable());
    }
}
