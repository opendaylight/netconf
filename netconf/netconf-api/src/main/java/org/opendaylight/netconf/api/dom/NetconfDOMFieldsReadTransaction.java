/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.dom;

import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;

/**
 *  NETCONF DOM read-only transaction with an option to define specific fields to read.
 */
public interface NetconfDOMFieldsReadTransaction extends DOMDataTreeReadTransaction, NetconfDOMFieldsReadOperations {
}