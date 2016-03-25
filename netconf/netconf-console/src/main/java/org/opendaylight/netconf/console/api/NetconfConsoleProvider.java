/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.api;

import java.util.List;
import java.util.Map;

public interface NetconfConsoleProvider extends AutoCloseable {

    Map<String, Map<String, String>> listDevices();

    Map<String, Map<String, List<String>>> showDevice(String deviceIp, String devicePort);

    boolean connectDevice(String deviceIp, String devicePort, String username, String password, Boolean tcpOnly);

    boolean disconnectDevice(String deviceIp, String devicePort);

    boolean updateDevice(String deviceIp, String devicePort, String username, String password);

}
