/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.messages.transactions;

import java.io.Serializable;

/**
 * API for transaction request messages, slave sends these message types to master for performing required operation.
 * This interface helps better handle request messages in actor. All messages are send with operations defined in
 * NetconfProxyDOMTransaction. Messages requiring response are send by ask otherwise with tell.
 */
public interface TransactionRequest extends Serializable {
}
