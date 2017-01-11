/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.protocol;

import static org.junit.Assert.assertEquals;

import java.security.GeneralSecurityException;
import java.security.PublicKey;

import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netconf.callhome.protocol.AuthorizedKeysDecoder;


public class AuthorizedKeysDecoderTest {

    AuthorizedKeysDecoder instance;

    @Before
    public void setup() {
        instance = new AuthorizedKeysDecoder();
    }

    @Test
    public void authorizedKeysDecoderValidRSAKey () throws GeneralSecurityException {
        // given
        String rsaStr = "AAAAB3NzaC1yc2EAAAADAQABAAABAQCvLigTfPZMqOQwHp051Co4lwwPwO21NFIXWgjQmCPEgRTqQpei7qQaxlLGkrIPjZtJQRgCuC+Sg8HFw1YpUaMybN0nFInInQLp/qe0yc9ByDZM2G86NX6W5W3+j87I8Fh1dnMov1iJ0DFVn8RLwdEGjreiZCRyJOMuHghh6y4EG7W8BwmZrse17zhSpc2wFOVhxeZnYAQFEw6g48LutFRDpoTjGgz1nz/L4zcaUxxigs8wdY+qTTOHxSTxlLqwSZPFLyYrV2KJ9mKahMuYUy6o2b8snsjvnSjyK0kY+U0C6c8fmPDFUc0RqJqfdnsIUyh11U8d3NZdaFWg0UW0SNK3";
        // when
        PublicKey serverKey = instance.decodePublicKey(rsaStr);
        // then
        assertEquals(serverKey.getAlgorithm(), "RSA");
    }

    @Test(expected = Exception.class)
    public void authorizedKeysDecoderInvalidRSAKey () throws GeneralSecurityException {
        // given
        String rsaStr = "AAAB3NzaC1yc2EAAAADAQABAAABAQCvLigTfPZMqOQwHp051Co4lwwPwO21NFIXWgjQmCPEgRTqQpei7qQaxlLGkrIPjZtJQRgCuC+Sg8HFw1YpUaMybN0nFInInQLp/qe0yc9ByDZM2G86NX6W5W3+j87I8Fh1dnMov1iJ0DFVn8RLwdEGjreiZCRyJOMuHghh6y4EG7W8BwmZrse17zhSpc2wFOVhxeZnYAQFEw6g48LutFRDpoTjGgz1nz/L4zcaUxxigs8wdY+qTTOHxSTxlLqwSZPFLyYrV2KJ9mKahMuYUy6o2b8snsjvnSjyK0kY+U0C6c8fmPDFUc0RqJqfdnsIUyh11U8d3NZdaFWg0UW0SNK3";
        // when
        instance.decodePublicKey(rsaStr);
    }

    @Test
    public void authorizedKeysDecoderValidDSAKey() throws GeneralSecurityException {
        // given
        String dsaStr = "AAAAB3NzaC1kc3MAAACBANkM1e45lxlyV24QyWBAoESlHzhYYJUfk/yUd0+Dv28okyO71DmnJesYyUzsKDpnFLlnFhxTTUGSg90fdrdubLFkRTGnHhweegMCf6kU1xyE3U6bpyMdiOXH7fOS6Q2B+qtaQRB4R5TEhdoJX648Ng+YZvLwdbZh3r/et4P46b3DAAAAFQDcu6qp67XRpzMoOS2fIL+VOxvmDwAAAIAeT3d/hbvzPoL8wV52gPtWJMU2EGoX/LJwc86Vn52NlxXB1EQSzZI50PgCKEckS80lj4GXO1ZyuBhdsBEz4rDtAIdZGW5z7WxTfcz0G2dOWmNOBqvu7j9ngfPrgtDVHYV2VL/4VpbmoPgkQLfbA9NWb6US2RnTO46rGbGurigDMQAAAIEAiI3REuOJAmgDow6HxbN0FM+RCe1JYDwJIsCRRK4JA9oYV4Pg897xqypOeXogutVu9usfcOJI6uk5OwwLqIUSaU+flgmL0LOXv4lH4+URqs7Or8+ABFTcVGGCxg0I3gwhlY2Vjc9nyHY15wqBYdUxLbe8HC6EQp9uwlLlb8LQ6a0=";
        // when
        PublicKey serverKey = instance.decodePublicKey(dsaStr);
        // then
        assertEquals(serverKey.getAlgorithm(), "DSA");
    }

    @Test(expected = IllegalArgumentException.class)
    public void authorizedKeysDecoderInvalidDSAKey() throws GeneralSecurityException {
        // given
        String dsaStr = "AAAAB3Nzakc3MAAACBANkM1e45lxlyV24QyWBAoESlHzhYYJUfk/yUd0+Dv28okyO71DmnJesYyUzsKDpnFLlnFhxTTUGSg90fdrdubLFkRTGnHhweegMCf6kU1xyE3U6bpyMdiOXH7fOS6Q2B+qtaQRB4R5TEhdoJX648Ng+YZvLwdbZh3r/et4P46b3DAAAAFQDcu6qp67XRpzMoOS2fIL+VOxvmDwAAAIAeT3d/hbvzPoL8wV52gPtWJMU2EGoX/LJwc86Vn52NlxXB1EQSzZI50PgCKEckS80lj4GXO1ZyuBhdsBEz4rDtAIdZGW5z7WxTfcz0G2dOWmNOBqvu7j9ngfPrgtDVHYV2VL/4VpbmoPgkQLfbA9NWb6US2RnTO46rGbGurigDMQAAAIEAiI3REuOJAmgDow6HxbN0FM+RCe1JYDwJIsCRRK4JA9oYV4Pg897xqypOeXogutVu9usfcOJI6uk5OwwLqIUSaU+flgmL0LOXv4lH4+URqs7Or8+ABFTcVGGCxg0I3gwhlY2Vjc9nyHY15wqBYdUxLbe8HC6EQp9uwlLlb8LQ6a0=";
        // when
        instance.decodePublicKey(dsaStr);
    }

    @Test
    public void authorizedKeysDecoderValidECDSAKey() throws GeneralSecurityException {
        // given
        String ecdsaStr = "AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBAP4dTrlwZmz8bZ1f901qWuFk7YelrL2WJG0jrCEAPo9UNM1wywpqjbaYUfoq+cevhLZaukDQ4N2Evux+YQ2zz0=";
        // when
        PublicKey serverKey = instance.decodePublicKey(ecdsaStr);
        // then
        assertEquals(serverKey.getAlgorithm(), "EC");
    }

    @Test(expected = IllegalArgumentException.class)
    public void authorizedKeysDecoderInvalidECDSAKey() throws GeneralSecurityException {
        // given
        String ecdsaStr = "AAAAE2VjZHNhLXNoItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBAP4dTrlwZmz8bZ1f901qWuFk7YelrL2WJG0jrCEAPo9UNM1wywpqjbaYUfoq+cevhLZaukDQ4N2Evux+YQ2zz0=";
        // when
        instance.decodePublicKey(ecdsaStr);
    }

    @Test(expected = IllegalArgumentException.class)
    public void authorizedKeysDecoderInvalidKeyType() throws GeneralSecurityException {
        // given
        String ed25519Str = "AAAAC3NzaC1lZDI1NTE5AAAAICIvyX9C+u3KZmJ8x4DuqJg1iAKOPObCgkX9plrvu29R";
        // when
        instance.decodePublicKey(ed25519Str);
    }

    @Test(expected = IllegalArgumentException.class)
    public void decodingOfBlankInputIsCaughtAsAnError() throws GeneralSecurityException {
        // when
        instance.decodePublicKey("");
   }
}
