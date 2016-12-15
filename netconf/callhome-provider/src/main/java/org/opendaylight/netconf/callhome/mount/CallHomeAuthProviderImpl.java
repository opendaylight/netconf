/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.mount;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import java.net.SocketAddress;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.netconf.callhome.protocol.AuthorizedKeysDecoder;
import org.opendaylight.netconf.callhome.protocol.CallHomeAuthorization;
import org.opendaylight.netconf.callhome.protocol.CallHomeAuthorizationProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.Admin;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.Devices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.admin.AllDevicesCredentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.devices.Device;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


class CallHomeAuthProviderImpl implements CallHomeAuthorizationProvider {
    private static final Logger LOG = LoggerFactory.getLogger(CallHomeAuthProviderImpl.class);

    private final DataBroker dataBroker;
    private final  Map<String, PublicKey> storedKeyMap = new HashMap<>();

    @VisibleForTesting
    AuthorizedKeysDecoder decoder = new AuthorizedKeysDecoder();

    public CallHomeAuthProviderImpl(DataBroker broker) {
        this.dataBroker = broker;
    }

    @Override
    public CallHomeAuthorization provideAuth(SocketAddress remoteAddress, PublicKey serverKey) {
        // Compare the callhome client's key (device) to the keys stored in server.
        List<Device> devices = getAllDevices();
        if (devices.size() == 0) {
            LOG.warn("Call home Device keys not configured.");
            return CallHomeAuthorization.rejected();
        }

        for (Device device : devices) {
            String storedKeyStr = device.getSshHostKey();
            PublicKey storedPubKey = this.storedKeyMap.get(storedKeyStr);

            if (storedPubKey == null) {
                PublicKey decodedKey = null;
                try {
                    decodedKey = decoder.decodePublicKey(storedKeyStr);
                } catch (InvalidKeySpecException e) {
                    LOG.error("Invalid key specification for stored publicKey of device " + device.getUniqueId(), e);
                    continue;
                } catch (NoSuchAlgorithmException e) {
                    LOG.error("No such algorithm for stored publicKey of device " + device.getUniqueId(), e);
                    continue;
                } catch (Exception ex) {
                    LOG.error("Malformed key for device " + device.getUniqueId(), ex);
                    continue;
                }

                if (decodedKey == null) {
                    LOG.error("Not able to decode key for ", storedKeyStr);
                    continue;
                }

                this.storedKeyMap.put(storedKeyStr, decodedKey);
            }

            storedPubKey = this.storedKeyMap.get(storedKeyStr);
            if (storedPubKey.getAlgorithm().equals(serverKey.getAlgorithm()) && storedPubKey.equals(serverKey)) {
                String[] candidate = findSharedUserAndPassword();
                if (areValidUserPassword(candidate)) {
                    LOG.info("Found a non-blank username and password for device at address: {}",
                            remoteAddress.toString());
                    return CallHomeAuthorization.serverAccepted(candidate[0]).addPassword(candidate[1]).build();
                } else {
                    LOG.info("The user name or password is blank so cannot be used in call home.");
                    break;
                }
            }
        }

        return CallHomeAuthorization.rejected();
    }

    // FIXME: This should be listener instead of read
    private List<Device> getAllDevices() {
        try(ReadOnlyTransaction rxTransaction = dataBroker.newReadOnlyTransaction()) {
            CheckedFuture<Optional<Devices>, ReadFailedException> devicesFuture =
                    rxTransaction.read(LogicalDatastoreType.CONFIGURATION, IetfZeroTouchCallHomeServerProvider.ALL_DEVICES);

            Optional<Devices> opt = devicesFuture.checkedGet();
            if (opt.isPresent()) {
                return opt.get().getDevice();
            }
        } catch (ReadFailedException e) {
            LOG.error("Error trying to read the whitelist devices: {}", e);
        }
        return Collections.emptyList();
    }

    private String[] findSharedUserAndPassword() {
        InstanceIdentifier<Admin> adminPath = InstanceIdentifier.create(Admin.class);
        InstanceIdentifier<AllDevicesCredentials> allDevicesCredentialsPath =
                adminPath.child(AllDevicesCredentials.class);


        try(ReadOnlyTransaction tx = dataBroker.newReadOnlyTransaction()) {
            CheckedFuture<Optional<AllDevicesCredentials>, ReadFailedException> future =
                    tx.read(LogicalDatastoreType.CONFIGURATION, allDevicesCredentialsPath);
            Optional<AllDevicesCredentials> opt = future.checkedGet();
            if (opt.isPresent()) {
                AllDevicesCredentials allDevCreds = opt.get();
                return new String[] {allDevCreds.getUsername(), allDevCreds.getPassword()};
            } else {
                LOG.error("No username and/or password for all-devices-credentials is present in call home server.");
            }
        } catch (ReadFailedException e) {
            LOG.error("Failure reading username or password for all-devices-credentials in call home server", e);
        }

        return new String[] {"", ""};
    }

    boolean areValidUserPassword(String[] candidate) {
        for (String part : candidate) {
            if (part == null || part.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }
};
