/*
 * Copyright (c) 2018 ZTE Corporation and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.openapi;

import java.util.Map;

public class MountPointInstance {
    private final String instance;
    private final Long id;

    public MountPointInstance(Map.Entry<String, Long> entry) {
        this.instance = entry.getKey();
        this.id = entry.getValue();
    }

    public String getInstance() {
        return instance;
    }

    public Long getId() {
        return id;
    }

}
