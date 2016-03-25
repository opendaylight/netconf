/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.api;

import java.util.List;

public interface NetconfConsoleProvider extends AutoCloseable {

    List<String> listDevices(boolean isConfigurationDatastore);

    List<String> showDevice(String deviceIp);

    boolean connectDevice(String deviceIp, String devicePort, String username, String password);

    boolean disconnectDevice(String deviceIp);

    boolean updateDevice(String deviceIp, String devicePort, String username, String password);
}
