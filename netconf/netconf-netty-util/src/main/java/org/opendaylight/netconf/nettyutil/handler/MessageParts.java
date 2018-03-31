/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.opendaylight.netconf.util.messages.NetconfMessageConstants;

/**
 * netconf message part constants as bytes.
 *
 * @author Thomas Pantelis
 */
interface MessageParts {
    byte[] END_OF_MESSAGE = NetconfMessageConstants.END_OF_MESSAGE.getBytes(UTF_8);
    byte[] START_OF_CHUNK = NetconfMessageConstants.START_OF_CHUNK.getBytes(UTF_8);
    byte[] END_OF_CHUNK = NetconfMessageConstants.END_OF_CHUNK.getBytes(UTF_8);
}
