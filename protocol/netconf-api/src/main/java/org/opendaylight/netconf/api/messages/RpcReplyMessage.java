/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.messages;

import org.opendaylight.netconf.api.NetconfMessage;
import org.w3c.dom.Document;

/**
 * A message carrying a {@code rpc-reply} response, as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc6241#section-4.1">RFC6241, section 4.2</a>.
 */
public final class RpcReplyMessage extends NetconfMessage {
    public RpcReplyMessage(final Document document) {
        super(document);
    }
}
