/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint.sal.changes;

import org.opendaylight.restconfsb.communicator.api.RestconfFacade;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public class ChangeFactory {

    private final RestconfFacade facade;

    public ChangeFactory(final RestconfFacade facade) {
        this.facade = facade;
    }

    public Change createPut(final YangInstanceIdentifier path, final NormalizedNode<?, ?> data) {
        return new Put(facade, path, data);
    }

    public Change createMerge(final YangInstanceIdentifier path, final NormalizedNode<?, ?> data) {
        return new Merge(facade, path, data);
    }

    public Change createDelete(final YangInstanceIdentifier path) {
        return new Delete(facade, path);
    }

    public Change create(final Type type, final YangInstanceIdentifier path, final NormalizedNode<?, ?> data) {
        switch (type) {
            case PUT:
                return createPut(path, data);
            case MERGE:
                return createMerge(path, data);
            case DELETE:
                return createDelete(path);
            default:
                throw new IllegalArgumentException("Unknown tyoe " + type);
        }
    }

    public enum Type {
        PUT,
        MERGE,
        DELETE
    }
}
