/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.util.osgi;

import com.google.common.base.Optional;
import io.netty.channel.local.LocalAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetconfConfigUtil {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfConfigUtil.class);

    private static final String PREFIX_PROP = "netconf.";

    private NetconfConfigUtil() {
    }

    public enum InfixProp {
        tcp, ssh
    }

    private static final String PORT_SUFFIX_PROP = ".port";
    private static final String ADDRESS_SUFFIX_PROP = ".address";
    private static final String PRIVATE_KEY_PATH_PROP = ".pk.path";

    private static final String CONNECTION_TIMEOUT_MILLIS_PROP = "connectionTimeoutMillis";
    private static final String LOCAL_HOST = "127.0.0.1";
    private static final String INADDR_ANY = "0.0.0.0";
    public static final long DEFAULT_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(30);
    private static final LocalAddress NETCONF_LOCAL_ADDRESS = new LocalAddress("netconf");
    public static final String DEFAULT_PRIVATE_KEY_PATH = "./configuration/RSA.pk";
    public static final InetSocketAddress DEFAULT_TCP_SERVER_ADRESS = new InetSocketAddress(LOCAL_HOST, 8383);
    public static final InetSocketAddress DEFAULT_SSH_SERVER_ADRESS = new InetSocketAddress(INADDR_ANY, 1830);

    public static LocalAddress getNetconfLocalAddress() {
        return NETCONF_LOCAL_ADDRESS;
    }

    public static long extractTimeoutMillis(final BundleContext bundleContext) {
        final String key = PREFIX_PROP + CONNECTION_TIMEOUT_MILLIS_PROP;
        final String timeoutString = bundleContext.getProperty(key);
        if (timeoutString == null || timeoutString.length() == 0) {
            return DEFAULT_TIMEOUT_MILLIS;
        }
        try {
            return Long.parseLong(timeoutString);
        } catch (final NumberFormatException e) {
            LOG.warn("Cannot parse {} property: {}, using defaults", key, timeoutString, e);
            return DEFAULT_TIMEOUT_MILLIS;
        }
    }

    /**
     * @param context from which properties are being read.
     * @return value of private key path if value is present, Optional.absent otherwise
     */
    public static Optional<String> getPrivateKeyPath(final BundleContext context) {
        return getProperty(context, getPrivateKeyKey());
    }

    public static String getPrivateKeyKey() {
        return PREFIX_PROP + InfixProp.ssh + PRIVATE_KEY_PATH_PROP;
    }

    public static String getNetconfServerAddressKey(final InfixProp infixProp) {
        return PREFIX_PROP + infixProp + ADDRESS_SUFFIX_PROP;
    }

    /**
     * @param context   from which properties are being read.
     * @param infixProp either tcp or ssh
     * @return value if address and port are present and valid, Optional.absent otherwise.
     */
    public static Optional<InetSocketAddress> extractNetconfServerAddress(final BundleContext context,
                                                                           final InfixProp infixProp) {

        final Optional<String> address = getProperty(context, getNetconfServerAddressKey(infixProp));
        final Optional<String> port = getProperty(context, PREFIX_PROP + infixProp + PORT_SUFFIX_PROP);

        if (address.isPresent() && port.isPresent()) {
            try {
                return Optional.of(parseAddress(address, port));
            } catch (final IllegalArgumentException | SecurityException e) {
                LOG.warn("Unable to parse {} netconf address from {}:{}, fallback to default",
                        infixProp, address, port, e);
            }
        }
        return Optional.absent();
    }

    private static InetSocketAddress parseAddress(final Optional<String> address, final Optional<String> port) {
        final int portNumber = Integer.valueOf(port.get());
        return new InetSocketAddress(address.get(), portNumber);
    }

    private static Optional<String> getProperty(final BundleContext context, final String propKey) {
        String value = context.getProperty(propKey);
        if (value != null && value.isEmpty()) {
            value = null;
        }
        return Optional.fromNullable(value);
    }

    public static java.util.Optional<NetconfConfiguration> getNetconfConfigurationService(BundleContext bundleContext) {
        final Collection<ServiceReference<ManagedService>> serviceReferences;
        try {
            serviceReferences = bundleContext.getServiceReferences(ManagedService.class, null);
            for (final ServiceReference<ManagedService> serviceReference : serviceReferences) {
                ManagedService service = bundleContext.getService(serviceReference);
                if (service instanceof NetconfConfiguration){
                    return java.util.Optional.of((NetconfConfiguration) service);
                }
            }
        } catch (InvalidSyntaxException e) {
            LOG.error("Unable to retrieve references for ManagedService: {}", e);
        }
        LOG.error("Unable to retrieve NetconfConfiguration service. Not found. Bundle netconf-util probably failed.");
        return java.util.Optional.empty();
    }
}
