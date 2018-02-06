/*
 * Copyright (c) 2018 ZTE Corporation. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.util;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.netconf.sal.connect.util.NetconfSalKeystoreService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.AddPrivateKeyInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.AddPrivateKeyInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.AddTrustedCertificateInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.AddTrustedCertificateInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017._private.keys.PrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017._private.keys.PrivateKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017._private.keys.PrivateKeyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.trusted.certificates.TrustedCertificate;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.trusted.certificates.TrustedCertificateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.trusted.certificates.TrustedCertificateKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;


public class NetconfSalKeystoreServiceTest {
    private static final String KEY_NAME = "test-private";
    private static final String TRUSTED_NAME = "test-trusted";

    private static final String PRIVATE_KEY =
            //"-----BEGIN PRIVATE KEY-----
            "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQCxrZcfyb8hPfiA\n"
          + "Sa1FNqboboPfgOv6E6KJpqpsgbowbEGnw6fOJnx9otfrVmAP86YUT6i+nuPwNG/b\n"
          + "I5YHqxd+OMllmvt0DX1tbtHMirx0wFcVXfk4hruDEMWzsUTohrpiQFTrq/kb9Dr4\n"
          + "FxO+/Ddt+YMmUhu1WGPAhOun2VErwGF6HuYfBMji3QvSzS0Hn9Fez1/nY88CzQrz\n"
          + "lPUH7FMVYIP/rzovKJT/5Bnj+YiiNMKYuPoyvBmyPylFeCvAhF5uyGDjLGYDkscQ\n"
          + "MzFDu44ebtcnVK9BZUxgWUDVwm5FjDSDjdcWKla7N46ykITvyBelm96oY38KkNaV\n"
          + "6x/e/4KZAgMBAAECggEBAJdNO2PWaOvl2bdlBifqYjeL5MBvCIPsNH0DcBz2W5bg\n"
          + "mQhDlKH4JArYoQXGiAcNdF/Xddrdcz0ZaicyJpBhIaBauyXK1FX/JtAJjp6fhdvl\n"
          + "7kJDw/ZexU7W+YQLcKKSGCWSor4NtBQZ5h1diXMZVBpSX1xCj1Xd7xQCHKrSZEzc\n"
          + "Geqx7ew1jkkHL+K6MunyQrY2fb6snWkyGEJf3VyjRjzzNubmF7dR7p+fo6NpeTD4\n"
          + "ywEAenkTtw55gwtz2q5pTV3I11ZJHcFjfbLY3SONz86x5FwNODVzaTrLN0JAvkXz\n"
          + "1LqeZbGRnKQ2/emlX1Gt9z8AzlissqJS8JgvQNm+AAECgYEA5bIEJfFDDvBWFPbh\n"
          + "gp5QmX9v9n2jRRX6mEdRPMIbfLfh9NAg3dc5l+yxQaAzSJHrOxe2eciwUGNhE5yb\n"
          + "Spub9o66ZSjAB58GzMI3LIrMUWufp5Ua7ztl3SuMhoTPlAMuqe69q4RMC0KUW9aE\n"
          + "LF4fs2fAm62dv15Z0pOsnR0CdSkCgYEAxgaVD055iAutKXSugI63hvP0T6DAHcnQ\n"
          + "rgdPUT4uCQGibnqUPRbcnNkBNxORRmap12K39B0QQ0eZTpdKNYmWOSnvNSBwiJ6r\n"
          + "ZRCF7UCqArub3nrONSYXXfd+szVjlYgB0paiOUtzEtt7s+JYV2+KswIAx0QwjynX\n"
          + "OPLVAF4tX/ECgYEAtPirwgUzU2rSN9RH2vTHBhlc6nUUlVL6zM2r2NYKeBoc4hi1\n"
          + "PHPdQbDP+6evoCavkjBdqdgP6lZSXvRNedveZsUPYLJZkeeeoOcIN4Tn8+J6uLuG\n"
          + "rCQ9XqN4JWgwcCqNsn+SWrdyfpCneTArlRVXnq9JFp8UoXlCBeIp5uO7UvkCgYEA\n"
          + "kE7Vq4zlldXkf/RvAnJ+nhMDtE+SEWMz9s6O58anZ5rQUzBy/L2/UXH2p7tTv/kq\n"
          + "xjJDmdrgMhdoSlSIGNHGLqw3jQCx4W23u3O6FXZtLoanhQ77XNIAb1Lf+xrqEltF\n"
          + "8MAjQhuQpWpbEHDfLgC0E9Ve2dgAhyPXmsGjpZv79xECgYAjX+cUFoexgkzhilSc\n"
          + "xyI2GI46IZ1NIFBCMuTMSTkEpBotO1ooUT4ZrR2mdIsoc3O5X5y63ZI1CWMwfd4u\n"
          + "buB0qy91s1i4AMW3JlB/jOX++y7YVwUX2aHHHl9fljFJBYqSxUatPDSuo0r42SXq\n"
          + "sD2PXwX43X/g2QxQhP2l9nfSmg==\n";
            //"-----END PRIVATE KEY-----";

    private static final String PUBKEY_CERTIFICATE =
            //"-----BEGIN CERTIFICATE-----\n"
            "MIIDgTCCAmmgAwIBAgIEb3Rj1DANBgkqhkiG9w0BAQsFADBxMQswCQYDVQQGEwJD\n"
          + "TjEQMA4GA1UECBMHSklBTkdTVTEQMA4GA1UEBxMHTkFOSklORzEVMBMGA1UEChMM\n"
          + "b3BlbmRheWxpZ2h0MRAwDgYDVQQLEwduZXRjb25mMRUwEwYDVQQDEwxvcGVuZGF5\n"
          + "bGlnaHQwHhcNMTcxMTIwMDkyNDM3WhcNMjcxMTE4MDkyNDM3WjBxMQswCQYDVQQG\n"
          + "EwJDTjEQMA4GA1UECBMHSklBTkdTVTEQMA4GA1UEBxMHTkFOSklORzEVMBMGA1UE\n"
          + "ChMMb3BlbmRheWxpZ2h0MRAwDgYDVQQLEwduZXRjb25mMRUwEwYDVQQDEwxvcGVu\n"
          + "ZGF5bGlnaHQwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCxrZcfyb8h\n"
          + "PfiASa1FNqboboPfgOv6E6KJpqpsgbowbEGnw6fOJnx9otfrVmAP86YUT6i+nuPw\n"
          + "NG/bI5YHqxd+OMllmvt0DX1tbtHMirx0wFcVXfk4hruDEMWzsUTohrpiQFTrq/kb\n"
          + "9Dr4FxO+/Ddt+YMmUhu1WGPAhOun2VErwGF6HuYfBMji3QvSzS0Hn9Fez1/nY88C\n"
          + "zQrzlPUH7FMVYIP/rzovKJT/5Bnj+YiiNMKYuPoyvBmyPylFeCvAhF5uyGDjLGYD\n"
          + "kscQMzFDu44ebtcnVK9BZUxgWUDVwm5FjDSDjdcWKla7N46ykITvyBelm96oY38K\n"
          + "kNaV6x/e/4KZAgMBAAGjITAfMB0GA1UdDgQWBBTjjrH57CtARfDWvpmkmv7lGL/2\n"
          + "rDANBgkqhkiG9w0BAQsFAAOCAQEApzyNLC/jh5UhWh9VvQW19Fbn9zTcSiCRtgwf\n"
          + "eUXpQPukLfL0eDOK0kBLIvN8lQtu7nH/3aYTaZwHMOpahDfSe2Q0eZPxr9hX7Hlo\n"
          + "kVOtQ88iW3c/KUF7TVjC16eBeNqvMsBCZ45j0al3QxP40iFPvO576HxRLuYKaB3k\n"
          + "+CNI6bn1m5SKoToQgbQbqrALFE3zud7iaUSiLAPSCLBqvEg50pLbbxc6nHXmHjqp\n"
          + "alTeRo89Ph0k0jnHzsW+GlefH4MGS3E2MZG0i1jh/+JoTC6DV2g/vNI4Yry7YFa0\n"
          + "WCCnuFcJwlvd12rkxiNI78buwMzEGcOU16wXeDRJxOM+UbF9xA==\n";
            //"-----END CERTIFICATE-----";

    private static final String TRUSTED_CERTIFICATE =
            //"-----BEGIN CERTIFICATE-----\n"
            "MIIECTCCAvGgAwIBAgIBCDANBgkqhkiG9w0BAQsFADCBjDELMAkGA1UEBhMCQ1ox\n"
          + "FjAUBgNVBAgMDVNvdXRoIE1vcmF2aWExDTALBgNVBAcMBEJybm8xDzANBgNVBAoM\n"
          + "BkNFU05FVDEMMAoGA1UECwwDVE1DMRMwEQYDVQQDDApleGFtcGxlIENBMSIwIAYJ\n"
          + "KoZIhvcNAQkBFhNleGFtcGxlY2FAbG9jYWxob3N0MB4XDTE1MDczMDA3MjU1MFoX\n"
          + "DTM1MDcyNTA3MjU1MFowgYUxCzAJBgNVBAYTAkNaMRYwFAYDVQQIDA1Tb3V0aCBN\n"
          + "b3JhdmlhMQ8wDQYDVQQKDAZDRVNORVQxDDAKBgNVBAsMA1RNQzEXMBUGA1UEAwwO\n"
          + "ZXhhbXBsZSBzZXJ2ZXIxJjAkBgkqhkiG9w0BCQEWF2V4YW1wbGVzZXJ2ZXJAbG9j\n"
          + "YWxob3N0MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAsdI1TBjzX1Pg\n"
          + "QXFuPCw5/kQwU7qkrhirMcFAXhI8EoXepPa9fKAVuMjHW32P6nNzDpnhFe0YGdNl\n"
          + "oIEN3hJJ87cVOqj4o7zZMbq3zVG2L8As7MTA8tYXm2fSC/0rIxxRRemcGUXM0q+4\n"
          + "LEACjZj2pOKonaivF5VbhgNjPCO1Jj/TamUc0aViE577C9L9EiObGM+bGbabWk/K\n"
          + "WKLsvxUc+sKZXaJ7psTVgpggJAkUszlmwOQgFiMSR53E9/CAkQYhzGVCmH44Vs6H\n"
          + "zs3RZjOTbce4wr4ongiA5LbPeSNSCFjy9loKpaE1rtOjkNBVdiNPCQTmLuODXUTK\n"
          + "gkeL+9v/OwIDAQABo3sweTAJBgNVHRMEAjAAMCwGCWCGSAGG+EIBDQQfFh1PcGVu\n"
          + "U1NMIEdlbmVyYXRlZCBDZXJ0aWZpY2F0ZTAdBgNVHQ4EFgQU83qEtQDFzDvLoaII\n"
          + "vqiU6k7j1uswHwYDVR0jBBgwFoAUc1YQIqjZsHVwlea0AB4N+ilNI2gwDQYJKoZI\n"
          + "hvcNAQELBQADggEBAJ+QOLi4gPWGofMkLTqSsbv5xRvTw0xa/sJnEeiejtygAu3o\n"
          + "McAsyevSH9EYVPCANxzISPzd9SFaO56HxWgcxLn9vi8ZNvo2wIp9zucNu285ced1\n"
          + "K/2nDZfBmvBxXnj/n7spwqOyuoIc8sR7P7YyI806Qsfhk3ybNZE5UHJFZKDRQKvR\n"
          + "J1t4nk9saeo87kIuNEDfYNdwYZzRfXoGJ5qIJQK+uJJv9noaIhfFowDW/G14Ji5p\n"
          + "Vh/YtvnOPh7aBjOj8jmzk8MqzK+TZgT7GWu48Nd/NaV8g/DNg9hlN047LaNsJly3\n"
          + "NX3+VBlpMnA4rKwl1OnmYSirIVh9RJqNwqe6k/k=\n";
            //"-----END CERTIFICATE-----"

    @Mock
    private DataBroker dataBroker;

    @Mock
    private AAAEncryptionService encryptionService;

    private NetconfSalKeystoreService keystoreService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        final WriteTransaction wtx = mock(WriteTransaction.class);
        when(dataBroker.newWriteOnlyTransaction()).thenReturn(wtx);
        doNothing().when(wtx)
                .merge(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(DataObject.class));
        when(wtx.submit()).thenReturn(Futures.<Void, TransactionCommitFailedException>immediateCheckedFuture(null));

        keystoreService = new NetconfSalKeystoreService(dataBroker, encryptionService);
    }


    @Test
    public void testAddPrivateKey() throws Exception {
        final WriteTransaction wtx = mock(WriteTransaction.class);
        when(dataBroker.newWriteOnlyTransaction()).thenReturn(wtx);
        doNothing().when(wtx)
                .merge(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(DataObject.class));
        when(wtx.submit()).thenReturn(Futures.<Void, TransactionCommitFailedException>immediateCheckedFuture(null));

        final AddPrivateKeyInput input = getPrivateKeyInput();
        keystoreService.addPrivateKey(input);

        final InstanceIdentifier<Keystore> keystoreIid = InstanceIdentifier.create(Keystore.class);
        final PrivateKey privateKey = input.getPrivateKey().iterator().next();
        verify(wtx).merge(LogicalDatastoreType.CONFIGURATION,
                keystoreIid.child(PrivateKey.class, new PrivateKeyKey(privateKey.getName())), privateKey);
    }

    @Test
    public void testAddTrustedCertificate() throws Exception {
        final WriteTransaction wtx = mock(WriteTransaction.class);
        when(dataBroker.newWriteOnlyTransaction()).thenReturn(wtx);
        doNothing().when(wtx)
                .merge(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(DataObject.class));
        when(wtx.submit()).thenReturn(Futures.<Void, TransactionCommitFailedException>immediateCheckedFuture(null));

        final AddTrustedCertificateInput input = getTrustedCertificateInput();
        keystoreService.addTrustedCertificate(input);

        final InstanceIdentifier<Keystore> keystoreIid = InstanceIdentifier.create(Keystore.class);
        final TrustedCertificate certificate = input.getTrustedCertificate().iterator().next();
        verify(wtx).merge(LogicalDatastoreType.CONFIGURATION,
                keystoreIid.child(TrustedCertificate.class, new TrustedCertificateKey(certificate.getName())),
                certificate);
    }

    private AddPrivateKeyInput getPrivateKeyInput() {
        final List<PrivateKey> privateKeys = new ArrayList<>();
        final PrivateKey privateKey = new PrivateKeyBuilder()
                                        .setKey(new PrivateKeyKey(KEY_NAME))
                                        .setName(KEY_NAME)
                                        .setData(PRIVATE_KEY)
                                        .setCertificateChain(Arrays.asList(PUBKEY_CERTIFICATE))
                                        .build();
        privateKeys.add(privateKey);
        return new AddPrivateKeyInputBuilder().setPrivateKey(privateKeys).build();

    }

    private AddTrustedCertificateInput getTrustedCertificateInput() {
        final List<TrustedCertificate> trustedCertificates = new ArrayList<>();
        final TrustedCertificate certificate = new TrustedCertificateBuilder()
                                        .setKey(new TrustedCertificateKey(TRUSTED_NAME))
                                        .setName(TRUSTED_NAME)
                                        .setCertificate(TRUSTED_CERTIFICATE)
                                        .build();
        trustedCertificates.add(certificate);
        return new AddTrustedCertificateInputBuilder().setTrustedCertificate(trustedCertificates).build();
    }
}
