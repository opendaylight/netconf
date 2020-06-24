/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol.tls;

import java.security.cert.Certificate;
import java.util.Set;

public interface TlsAllowedDevicesMonitor extends AutoCloseable {

    /**
     * Return a Call-Home DeviceID by the provided certificate.
     */
    String findDeviceIdByCertificate(Certificate certificate);

    /**
     * Return a set of IDs for the keys associated with Call-Home devices.
     */
    Set<String> findAllowedKeys();

    @Override
    void close();

}
