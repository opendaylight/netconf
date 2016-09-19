/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.commands;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Strings;

public class NetconfCommandUtils {

    private static final Pattern IP_PATTERN = Pattern.compile(
            "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                    "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                    "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                    "([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");

    public static boolean isPortValid(final String devicePort) {
        if (Strings.isNullOrEmpty(devicePort)) {
            return false;
        }
        Integer port = null;
        try {
            port = Integer.parseInt(devicePort);
        } catch (NumberFormatException e){
          return false;
        }
        if (port != null && port >= 0 && port <= 65535) {
            return true;
        }
        return false;
    }

    public static boolean isIpValid(final String deviceIp) {
        if (Strings.isNullOrEmpty(deviceIp)) {
            return false;
        }
        Matcher matcher = IP_PATTERN.matcher(deviceIp);
        return matcher.matches();
    }
}
