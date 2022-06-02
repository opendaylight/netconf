/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.messages;

import com.google.common.annotations.Beta;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.NetconfMessage;
import org.w3c.dom.Document;

/**
 * Start-exi netconf message.
 */
@Beta
public final class NetconfStartExiMessage extends NetconfMessage {
    public static final @NonNull String START_EXI = "start-exi";

    public NetconfStartExiMessage(final Document doc) {
        super(doc);
    }
}
