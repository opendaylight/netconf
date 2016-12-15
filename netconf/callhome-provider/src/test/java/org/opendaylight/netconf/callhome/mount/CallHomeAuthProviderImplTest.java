/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.mount;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import java.net.SocketAddress;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.netconf.callhome.protocol.AuthorizedKeysDecoder;
import org.opendaylight.netconf.callhome.protocol.CallHomeAuthorization;
import org.opendaylight.yang.gen.v1.urn.brocade.params.xml.ns.yang.zerotouch.callhome.server.rev161109.Devices;
import org.opendaylight.yang.gen.v1.urn.brocade.params.xml.ns.yang.zerotouch.callhome.server.rev161109.admin.AllDevicesCredentials;
import org.opendaylight.yang.gen.v1.urn.brocade.params.xml.ns.yang.zerotouch.callhome.server.rev161109.devices.Device;
import org.opendaylight.yang.gen.v1.urn.brocade.params.xml.ns.yang.zerotouch.callhome.server.rev161109.devices.DeviceKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;


public class CallHomeAuthProviderImplTest {
    SocketAddress mockAddress;
    PublicKey mockKey;
    DataBroker mockBroker;
    ReadOnlyTransaction mockTx;
    CheckedFuture<Optional<AllDevicesCredentials>, ReadFailedException> mockFutureCreds;
    Optional<AllDevicesCredentials> mockOptCreds;
    AllDevicesCredentials mockCredentials;

    AuthorizedKeysDecoder decoder;
    String ENCODED_RSA =
            "AAAAB3NzaC1yc2EAAAADAQABAAABAQCvLigTfPZMqOQwHp051Co4lwwPwO21NFIXWgjQmCPEgRTqQpei7qQaxlLGkrIPjZtJQRgCuC+Sg8HFw1YpUaMybN0nFInInQLp/qe0yc9ByDZM2G86NX6W5W3+j87I8Fh1dnMov1iJ0DFVn8RLwdEGjreiZCRyJOMuHghh6y4EG7W8BwmZrse17zhSpc2wFOVhxeZnYAQFEw6g48LutFRDpoTjGgz1nz/L4zcaUxxigs8wdY+qTTOHxSTxlLqwSZPFLyYrV2KJ9mKahMuYUy6o2b8snsjvnSjyK0kY+U0C6c8fmPDFUc0RqJqfdnsIUyh11U8d3NZdaFWg0UW0SNK3";

    CheckedFuture<Optional<Devices>, ReadFailedException> mockFutureDevices;
    Optional<Devices> mockOptDevices;
    Devices mockDevices;
    DeviceKey mockDevKey;
    PublicKey sshHostKey;

    CallHomeAuthProviderImpl instance;

    @Before
    public void setup() throws InvalidKeySpecException, NoSuchAlgorithmException {
        mockAddress = mock(SocketAddress.class);
        mockKey = mock(PublicKey.class);
        mockBroker = mock(DataBroker.class);
        mockTx = mock(ReadOnlyTransaction.class);
        mockFutureCreds = mock(CheckedFuture.class);
        mockOptCreds = mock(Optional.class);
        mockCredentials = mock(AllDevicesCredentials.class);

        AuthorizedKeysDecoder decoder = new AuthorizedKeysDecoder();
        sshHostKey = decoder.decodePublicKey(ENCODED_RSA);

        mockFutureDevices = mock(CheckedFuture.class);
        mockOptDevices = mock(Optional.class);
        mockDevices = mock(Devices.class);
        mockDevKey = mock(DeviceKey.class);

        instance = new CallHomeAuthProviderImpl(mockBroker);
    }

    void happyPathMocking() throws ReadFailedException {
        when(mockBroker.newReadOnlyTransaction()).thenReturn(mockTx);
        when(mockTx.read(any(LogicalDatastoreType.class), any(InstanceIdentifier.class))).thenReturn(mockFutureDevices,
                mockFutureCreds);

        when(mockFutureCreds.checkedGet()).thenReturn(mockOptCreds);
        when(mockOptCreds.isPresent()).thenReturn(true);
        when(mockOptCreds.get()).thenReturn(mockCredentials);
        when(mockCredentials.getUsername()).thenReturn("not-blank-user-name");
        when(mockCredentials.getPassword()).thenReturn("not-blank-password");

        List<Device> devices = new ArrayList<Device>();
        devices.add(someDevice(mockDevKey, "dev#1", ENCODED_RSA));

        when(mockFutureDevices.checkedGet()).thenReturn(mockOptDevices);
        when(mockOptDevices.isPresent()).thenReturn(true);
        when(mockOptDevices.get()).thenReturn(mockDevices);
        when(mockDevices.getDevice()).thenReturn(devices);


    }

    Device someDevice(DeviceKey key, String uid, String sshHostKey) {
        Device d = mock(Device.class);
        when(d.getKey()).thenReturn(key);
        when(d.getUniqueId()).thenReturn(uid);
        when(d.getSshHostKey()).thenReturn(sshHostKey);
        return d;
    }

    @Test
    public void SuccessfulAuthorizationShouldBeBasedOnNonBlankUserNameAndPasswordAndHitOnWhitelist()
            throws ReadFailedException {
        // given
        happyPathMocking();
        // when
        CallHomeAuthorization result = instance.provideAuth(mockAddress, sshHostKey);
        // then
        assertTrue(result.isServerAllowed());
    }

    @Test
    public void UnsuccessfulAuthorizationShouldResultFromBadUserNameAndPasswordAndHitOnWhitelist()
            throws ReadFailedException {
        // given
        happyPathMocking();
        when(mockCredentials.getUsername()).thenReturn(""); // override mock
        when(mockCredentials.getPassword()).thenReturn("");
        // when
        CallHomeAuthorization result = instance.provideAuth(mockAddress, sshHostKey);
        // then
        assertFalse(result.isServerAllowed());
    }

    @Test
    public void UnsuccessfulAuthorizationShouldResultFromGoodUserNameAndPasswordAndMissOnWhitelist()
            throws ReadFailedException {
        // given
        happyPathMocking();
        when(mockDevices.getDevice()).thenReturn(new ArrayList<Device>()); // override whitelist to
                                                                           // be empty
        // when
        CallHomeAuthorization result = instance.provideAuth(mockAddress, sshHostKey);
        // then
        assertFalse(result.isServerAllowed());
    }

    // WTF? IDE ok, but command line is complaining about array initializer syntax?

    String[] newStrings(String... values) {
        return values;
    }

    boolean[] newBooleans(boolean... values) {
        return values;
    }

    @Test
    public void ValidCredentials() {
        String[] user = newStrings("a", "a", "", null, "a", " ");
        String[] pwd = newStrings("b", "", "b", "b", null, " ");
        boolean[] valid = newBooleans(true, false, false, false, false, false);

        for (int pass = 0; pass < user.length; pass++) {
            assertEquals(valid[pass], instance.areValidUserPassword(newStrings(user[pass], pwd[pass])));
        }
    }

    @Test
    public void UnsuccessfulAuthorizationShouldResultFromFailureOfDataStoreRead() throws ReadFailedException {
        // given
        happyPathMocking();
        ReadFailedException readFailed = new ReadFailedException("unit-test");
        when(mockFutureCreds.checkedGet()).thenThrow(readFailed); // override mock
        // when
        CallHomeAuthorization result = instance.provideAuth(mockAddress, mockKey);
        // then
        assertFalse(result.isServerAllowed());
    }

    @Test
    public void UnsuccessfulAuthorizationShouldResultFromMissingData() throws ReadFailedException {
        // given
        happyPathMocking();
        when(mockOptCreds.isPresent()).thenReturn(false); // override mock
        // when
        CallHomeAuthorization result = instance.provideAuth(mockAddress, mockKey);
        // then
        assertFalse(result.isServerAllowed());
    }

    @Test
    public void UnsuccessfulAuthorizationShouldResultFromMissingDeviceData() throws ReadFailedException {
        // given
        happyPathMocking();
        when(mockOptDevices.isPresent()).thenReturn(false); // override mock
        // when
        CallHomeAuthorization result = instance.provideAuth(mockAddress, mockKey);
        // then
        assertFalse(result.isServerAllowed());
    }

    @Test
    public void UnsuccessfulAuthorizationShouldResultFromEmptyDeviceData() throws ReadFailedException {
        // given
        happyPathMocking();
        when(mockOptDevices.get()).thenReturn(null); // override mock
        // when
        CallHomeAuthorization result = instance.provideAuth(mockAddress, mockKey);
        // then
        assertFalse(result.isServerAllowed());
    }

    Exception[] newExceptions(Exception... values) {
        return values;
    }

    @Test
    public void UnsuccessfulAuthorizationShouldResultFromVariousInvalidKeyModes() throws Exception {
        Exception[] err =
                newExceptions(new InvalidKeySpecException(), new NoSuchAlgorithmException(), new RuntimeException());

        for (int pass = 0; pass < err.length; pass++) {
            // given
            happyPathMocking();
            instance.decoder = mock(AuthorizedKeysDecoder.class);
            when(instance.decoder.decodePublicKey(anyString())).thenThrow(err[pass]);
            // when
            CallHomeAuthorization result = instance.provideAuth(mockAddress, mockKey);
            // then
            assertFalse(result.isServerAllowed());
        }
    }

    @Test
    public void UnsuccessfulAuthorizationShouldResultInANullKeyDecode() throws Exception {
        // given
        happyPathMocking();
        instance.decoder = mock(AuthorizedKeysDecoder.class);
        when(instance.decoder.decodePublicKey(anyString())).thenReturn(null);
        // when
        CallHomeAuthorization result = instance.provideAuth(mockAddress, mockKey);
        // then
        assertFalse(result.isServerAllowed());
    }
}
