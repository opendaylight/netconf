/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class AuthorizedKeysDecoderTest {
    private final AuthorizedKeysDecoder instance = new AuthorizedKeysDecoder();

    @Test
    public void authorizedKeysDecoderValidRSAKey() throws Exception {
        // given
        final var rsaStr = "AAAAB3NzaC1yc2EAAAADAQABAAABAQCvLigTfPZMqOQwHp051Co4lwwPwO21NFIXWgjQmCPEgRTqQpe"
                         + "i7qQaxlLGkrIPjZtJQRgCuC+Sg8HFw1YpUaMybN0nFInInQLp/qe0yc9ByDZM2G86NX6W5W3+j87I8F"
                         + "h1dnMov1iJ0DFVn8RLwdEGjreiZCRyJOMuHghh6y4EG7W8BwmZrse17zhSpc2wFOVhxeZnYAQFEw6g4"
                         + "8LutFRDpoTjGgz1nz/L4zcaUxxigs8wdY+qTTOHxSTxlLqwSZPFLyYrV2KJ9mKahMuYUy6o2b8snsjv"
                         + "nSjyK0kY+U0C6c8fmPDFUc0RqJqfdnsIUyh11U8d3NZdaFWg0UW0SNK3";
        // when
        final var serverKey = instance.decodePublicKey(rsaStr);
        // then
        assertEquals("RSA", serverKey.getAlgorithm());
    }

    @Test
    public void authorizedKeysDecoderInvalidRSAKey() {
        // given
        final var rsaStr = "AAAB3NzaC1yc2EAAAADAQABAAABAQCvLigTfPZMqOQwHp051Co4lwwPwO21NFIXWgjQmCPEgRTqQpei"
                         + "7qQaxlLGkrIPjZtJQRgCuC+Sg8HFw1YpUaMybN0nFInInQLp/qe0yc9ByDZM2G86NX6W5W3+j87I8Fh"
                         + "1dnMov1iJ0DFVn8RLwdEGjreiZCRyJOMuHghh6y4EG7W8BwmZrse17zhSpc2wFOVhxeZnYAQFEw6g48"
                         + "LutFRDpoTjGgz1nz/L4zcaUxxigs8wdY+qTTOHxSTxlLqwSZPFLyYrV2KJ9mKahMuYUy6o2b8snsjvn"
                         + "SjyK0kY+U0C6c8fmPDFUc0RqJqfdnsIUyh11U8d3NZdaFWg0UW0SNK3";
        // when
        final var ex = assertThrows(StringIndexOutOfBoundsException.class, () -> instance.decodePublicKey(rsaStr));
        assertEquals("offset 4, count 476, length 278", ex.getMessage());
    }

    @Test
    public void authorizedKeysDecoderValidDSAKey() throws Exception {
        // given
        final var dsaStr = "AAAAB3NzaC1kc3MAAACBANkM1e45lxlyV24QyWBAoESlHzhYYJUfk/yUd0+Dv28okyO71DmnJesYyUz"
                         + "sKDpnFLlnFhxTTUGSg90fdrdubLFkRTGnHhweegMCf6kU1xyE3U6bpyMdiOXH7fOS6Q2B+qtaQRB4R5"
                         + "TEhdoJX648Ng+YZvLwdbZh3r/et4P46b3DAAAAFQDcu6qp67XRpzMoOS2fIL+VOxvmDwAAAIAeT3d/h"
                         + "bvzPoL8wV52gPtWJMU2EGoX/LJwc86Vn52NlxXB1EQSzZI50PgCKEckS80lj4GXO1ZyuBhdsBEz4rDt"
                         + "AIdZGW5z7WxTfcz0G2dOWmNOBqvu7j9ngfPrgtDVHYV2VL/4VpbmoPgkQLfbA9NWb6US2RnTO46rGbG"
                         + "urigDMQAAAIEAiI3REuOJAmgDow6HxbN0FM+RCe1JYDwJIsCRRK4JA9oYV4Pg897xqypOeXogutVu9u"
                         + "sfcOJI6uk5OwwLqIUSaU+flgmL0LOXv4lH4+URqs7Or8+ABFTcVGGCxg0I3gwhlY2Vjc9nyHY15wqBY"
                         + "dUxLbe8HC6EQp9uwlLlb8LQ6a0=";
        // when
        final var serverKey = instance.decodePublicKey(dsaStr);
        // then
        assertEquals("DSA", serverKey.getAlgorithm());
    }

    @Test
    public void authorizedKeysDecoderInvalidDSAKey() {
        // given
        final var dsaStr = "AAAAB3Nzakc3MAAACBANkM1e45lxlyV24QyWBAoESlHzhYYJUfk/yUd0+Dv28okyO71DmnJesYyUzsK"
                         + "DpnFLlnFhxTTUGSg90fdrdubLFkRTGnHhweegMCf6kU1xyE3U6bpyMdiOXH7fOS6Q2B+qtaQRB4R5TE"
                         + "hdoJX648Ng+YZvLwdbZh3r/et4P46b3DAAAAFQDcu6qp67XRpzMoOS2fIL+VOxvmDwAAAIAeT3d/hbv"
                         + "zPoL8wV52gPtWJMU2EGoX/LJwc86Vn52NlxXB1EQSzZI50PgCKEckS80lj4GXO1ZyuBhdsBEz4rDtAI"
                         + "dZGW5z7WxTfcz0G2dOWmNOBqvu7j9ngfPrgtDVHYV2VL/4VpbmoPgkQLfbA9NWb6US2RnTO46rGbGur"
                         + "igDMQAAAIEAiI3REuOJAmgDow6HxbN0FM+RCe1JYDwJIsCRRK4JA9oYV4Pg897xqypOeXogutVu9usf"
                         + "cOJI6uk5OwwLqIUSaU+flgmL0LOXv4lH4+URqs7Or8+ABFTcVGGCxg0I3gwhlY2Vjc9nyHY15wqBYdU"
                         + "xLbe8HC6EQp9uwlLlb8LQ6a0=";
        // when
        final var ex = assertThrows(IllegalArgumentException.class, () -> instance.decodePublicKey(dsaStr));
        assertEquals("Last unit does not have enough valid bits", ex.getMessage());
    }

    @Test
    public void authorizedKeysDecoderValidEcDSAKey() throws Exception {
        // given
        final var ecdsaStr = "AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBAP4dTrlwZmz8bZ1f901qWuFk7Y"
                           + "elrL2WJG0jrCEAPo9UNM1wywpqjbaYUfoq+cevhLZaukDQ4N2Evux+YQ2zz0=";
        // when
        final var serverKey = instance.decodePublicKey(ecdsaStr);
        // then
        assertEquals("EC", serverKey.getAlgorithm());
    }

    @Test
    public void authorizedKeysDecoderInvalidEcDSAKey() {
        // given
        final var ecdsaStr = "AAAAE2VjZHNhLXNoItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBAP4dTrlwZmz8bZ1f901qWuFk7Yel"
                           + "rL2WJG0jrCEAPo9UNM1wywpqjbaYUfoq+cevhLZaukDQ4N2Evux+YQ2zz0=";
        // when
        final var ex = assertThrows(IllegalArgumentException.class, () -> instance.decodePublicKey(ecdsaStr));
        assertEquals("Last unit does not have enough valid bits", ex.getMessage());
    }

    @Test
    public void authorizedKeysDecoderInvalidKeyType() {
        // given
        final var ed25519Str = "AAAAC3NzaC1lZDI1NTE5AAAAICIvyX9C+u3KZmJ8x4DuqJg1iAKOPObCgkX9plrvu29R";
        // when
        final var ex = assertThrows(IllegalArgumentException.class, () -> instance.decodePublicKey(ed25519Str));
        assertEquals("Unknown decode key type ssh-ed25519 in " + ed25519Str, ex.getMessage());
    }

    @Test
    public void decodingOfBlankInputIsCaughtAsAnError() {
        // when
        final var ex = assertThrows(IllegalArgumentException.class, () -> instance.decodePublicKey(""));
        assertEquals("No Base64 part to decode in ", ex.getMessage());
    }
}
