/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.api.messages;

import javax.xml.transform.Transformer;

public class TransformerObject {

    private final Transformer transformer;
    private long lastUsed;
    private boolean isAvailable;

    public TransformerObject(Transformer transformer) {
        this.transformer = transformer;
        isAvailable = false;
        lastUsed = -1;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public void setAvailable(boolean available) {
        isAvailable = available;
        if (available) {
            lastUsed = System.currentTimeMillis();
        }
    }

    public Transformer getTransformer() {
        return transformer;
    }

    public long getLastUsed() {
        return lastUsed;
    }
}
