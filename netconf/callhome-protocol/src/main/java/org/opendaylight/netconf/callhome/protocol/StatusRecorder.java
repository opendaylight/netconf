/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.protocol;

import java.security.PublicKey;

/**
 * Records the status of the callhome device.
 */

public interface StatusRecorder {
    void reportFailedAuth(PublicKey sshKey);

    void reportTlsFailedCertificateMapping(String nodeId);
}
