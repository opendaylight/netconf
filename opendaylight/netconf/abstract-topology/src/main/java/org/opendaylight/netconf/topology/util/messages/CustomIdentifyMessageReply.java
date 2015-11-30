/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.util.messages;

import akka.actor.Address;

// Marker message, that signals that actor should not reply to this one
public class CustomIdentifyMessageReply extends CustomIdentifyMessage {
    private static final long serialVersionUID = 1L;

    public CustomIdentifyMessageReply(final Address addressFrom) {
        super(addressFrom);
    }
}
