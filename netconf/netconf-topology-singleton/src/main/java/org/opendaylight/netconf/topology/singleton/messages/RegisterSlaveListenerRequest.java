/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.messages;

import static java.util.stream.Collectors.toList;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

/**
 * Message is sended as reaction for registered listener on slave node. Master registers proper listener which will
 * listen on the specific SchemaType for slave.
 */
public class RegisterSlaveListenerRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    // Schema types parameters
    private final List<SchemaPathMessage> schemaPathMessages;

    public RegisterSlaveListenerRequest(final Collection<SchemaPath> schemaPaths) {
        // map to serializable list
        this.schemaPathMessages = schemaPaths.stream().map(SchemaPathMessage::new).collect(toList());
    }

    public List<SchemaPath> getSchemaPaths() {
        return schemaPathMessages.stream().map(type ->
                SchemaPath.create(type.getPath(), type.isAbsolute())).collect(toList());
    }
}
