/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.messages;

import java.io.Serial;
import java.io.Serializable;

/**
 * Due to possibility of race condition (when data-store is updated before data are initialized in master actor), only
 * when this message is received by master, operational data-store is changed.
 */
public class MasterActorDataInitialized implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
