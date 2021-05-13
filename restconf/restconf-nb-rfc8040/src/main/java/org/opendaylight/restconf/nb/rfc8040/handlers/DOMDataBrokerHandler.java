/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.handlers;

import static java.util.Objects.requireNonNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;

/**
 * Implementation of {@link DOMDataBrokerHandler}.
 */
// FIXME: remove this class
@Singleton
public class DOMDataBrokerHandler {
    private final DOMDataBroker broker;

    @Inject
    public DOMDataBrokerHandler(final DOMDataBroker broker) {
        this.broker = requireNonNull(broker);
    }

    public @NonNull DOMDataBroker get() {
        return broker;
    }
}
