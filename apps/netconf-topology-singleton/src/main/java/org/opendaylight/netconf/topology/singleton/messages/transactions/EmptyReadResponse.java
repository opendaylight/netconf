/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.messages.transactions;

import java.io.Serial;
import java.io.Serializable;

/**
 * Message is sended when read result do not present any value.
 */
public class EmptyReadResponse implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

}
