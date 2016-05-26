/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.references;

import java.lang.ref.SoftReference;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;

public class DOMDataBrokerRef {

    private final SoftReference<DOMDataBroker> ref;

    public DOMDataBrokerRef(final DOMDataBroker domDataBroker) {
        this.ref = new SoftReference<DOMDataBroker>(domDataBroker);
    }

    public DOMDataBroker get() {
        return this.ref.get();
    }

}
