/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.tx;

import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;

/**
 * Interfaces with a transaction back-end.
 *
 * @author Thomas Pantelis
 */
interface ProxyTransactionFacade extends DOMDataTreeReadWriteTransaction {
}
