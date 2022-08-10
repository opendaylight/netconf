/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.shaded.sshd.common.BaseBuilder;
import org.opendaylight.netconf.shaded.sshd.common.NamedFactory;
import org.opendaylight.netconf.shaded.sshd.common.kex.KeyExchangeFactory;
import org.opendaylight.netconf.shaded.sshd.common.mac.BuiltinMacs;
import org.opendaylight.netconf.shaded.sshd.common.mac.MacFactory;
import org.opendaylight.netconf.shaded.sshd.common.util.net.SshdSocketAddress;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.AbstractTransportStackBootstrap;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.Curve25519Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.Curve448Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroup14Sha1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroup14Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroup15Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroup16Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroup17Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroup18Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroup1Sha1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroupExchangeSha1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.DiffieHellmanGroupExchangeSha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.EcdhSha21284010045311;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.EcdhSha21313201;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.EcdhSha213132016;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.EcdhSha213132026;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.EcdhSha213132027;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.EcdhSha213132033;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.EcdhSha213132036;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.EcdhSha213132037;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.EcdhSha213132038;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.EcdhSha2Nistp256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.EcdhSha2Nistp384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.EcdhSha2Nistp521;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.EcmqvSha2;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.ExtInfoC;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.ExtInfoS;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve25519Sha2561284010045311;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve25519Sha2561313201;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve25519Sha25613132016;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve25519Sha25613132026;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve25519Sha25613132027;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve25519Sha25613132033;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve25519Sha25613132036;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve25519Sha25613132037;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve25519Sha25613132038;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve25519Sha256Curve25519Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve25519Sha256Curve448Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve25519Sha256Nistp256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve25519Sha256Nistp384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve25519Sha256Nistp521;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve448Sha5121284010045311;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve448Sha5121313201;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve448Sha51213132016;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve448Sha51213132026;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve448Sha51213132027;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve448Sha51213132033;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve448Sha51213132036;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve448Sha51213132037;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve448Sha51213132038;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve448Sha512Curve25519Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve448Sha512Curve448Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve448Sha512Nistp256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve448Sha512Nistp384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssCurve448Sha512Nistp521;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGexSha11284010045311;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGexSha11313201;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGexSha113132016;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGexSha113132026;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGexSha113132027;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGexSha113132033;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGexSha113132036;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGexSha113132037;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGexSha113132038;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGexSha1Curve25519Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGexSha1Curve448Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGexSha1Nistp256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGexSha1Nistp384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGexSha1Nistp521;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha11284010045311;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha11313201;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha113132016;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha113132026;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha113132027;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha113132033;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha113132036;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha113132037;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha113132038;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha1Curve25519Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha1Curve448Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha1Nistp256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha1Nistp384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha1Nistp521;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha2561284010045311;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha2561313201;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha25613132016;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha25613132026;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha25613132027;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha25613132033;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha25613132036;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha25613132037;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha25613132038;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha256Curve25519Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha256Curve448Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha256Nistp256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha256Nistp384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup14Sha256Nistp521;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup15Sha5121284010045311;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup15Sha5121313201;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup15Sha51213132016;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup15Sha51213132026;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup15Sha51213132027;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup15Sha51213132033;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup15Sha51213132036;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup15Sha51213132037;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup15Sha51213132038;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup15Sha512Curve25519Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup15Sha512Curve448Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup15Sha512Nistp256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup15Sha512Nistp384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup15Sha512Nistp521;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup16Sha5121284010045311;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup16Sha5121313201;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup16Sha51213132016;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup16Sha51213132026;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup16Sha51213132027;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup16Sha51213132033;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup16Sha51213132036;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup16Sha51213132037;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup16Sha51213132038;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup16Sha512Curve25519Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup16Sha512Curve448Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup16Sha512Nistp256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup16Sha512Nistp384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup16Sha512Nistp521;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup17Sha5121284010045311;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup17Sha5121313201;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup17Sha51213132016;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup17Sha51213132026;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup17Sha51213132027;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup17Sha51213132033;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup17Sha51213132036;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup17Sha51213132037;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup17Sha51213132038;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup17Sha512Curve25519Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup17Sha512Curve448Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup17Sha512Nistp256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup17Sha512Nistp384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup17Sha512Nistp521;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup18Sha5121284010045311;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup18Sha5121313201;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup18Sha51213132016;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup18Sha51213132026;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup18Sha51213132027;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup18Sha51213132033;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup18Sha51213132036;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup18Sha51213132037;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup18Sha51213132038;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup18Sha512Curve25519Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup18Sha512Curve448Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup18Sha512Nistp256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup18Sha512Nistp384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup18Sha512Nistp521;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup1Sha11284010045311;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup1Sha11313201;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup1Sha113132016;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup1Sha113132026;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup1Sha113132027;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup1Sha113132033;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup1Sha113132036;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup1Sha113132037;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup1Sha113132038;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup1Sha1Curve25519Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup1Sha1Curve448Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup1Sha1Nistp256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup1Sha1Nistp384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssGroup1Sha1Nistp521;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp256Sha2561284010045311;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp256Sha2561313201;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp256Sha25613132016;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp256Sha25613132026;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp256Sha25613132027;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp256Sha25613132033;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp256Sha25613132036;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp256Sha25613132037;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp256Sha25613132038;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp256Sha256Curve25519Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp256Sha256Curve448Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp256Sha256Nistp256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp256Sha256Nistp384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp256Sha256Nistp521;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp384Sha3841284010045311;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp384Sha3841313201;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp384Sha38413132016;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp384Sha38413132026;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp384Sha38413132027;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp384Sha38413132033;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp384Sha38413132036;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp384Sha38413132037;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp384Sha38413132038;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp384Sha384Curve25519Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp384Sha384Curve448Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp384Sha384Nistp256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp384Sha384Nistp384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp384Sha384Nistp521;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp521Sha5121284010045311;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp521Sha5121313201;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp521Sha51213132016;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp521Sha51213132026;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp521Sha51213132027;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp521Sha51213132033;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp521Sha51213132036;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp521Sha51213132037;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp521Sha51213132038;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp521Sha512Curve25519Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp521Sha512Curve448Sha512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp521Sha512Nistp256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp521Sha512Nistp384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.GssNistp521Sha512Nistp521;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.KeyExchangeAlgBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.Rsa1024Sha1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.key.exchange.algs.rev220616.Rsa2048Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.HmacMd5;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.HmacMd596;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.HmacSha1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.HmacSha196;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.HmacSha2256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.HmacSha2512;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh.mac.algs.rev220616.MacAlgBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev220718.SshClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev220718.TransportParamsGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev220718.transport.params.grouping.Encryption;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev220718.transport.params.grouping.HostKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev220718.transport.params.grouping.KeyExchange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev220718.transport.params.grouping.Mac;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev220718.SshServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev220524.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev220524.TcpServerGrouping;
import org.opendaylight.yangtools.concepts.Mutable;

/**
 * A bootstrap allowing instantiation of {@link SSHTransportStack}s.
 */
public abstract sealed class SSHTransportStackBootstrap extends AbstractTransportStackBootstrap {
    private static final class Client extends SSHTransportStackBootstrap {
        private final SshClientGrouping parameters;

        private Client(final TransportChannelListener listener, final SshClientGrouping parameters) {
            super(listener);
            this.parameters = requireNonNull(parameters);
        }

        @Override
        protected int tcpConnectPort() {
            return NETCONF_PORT;
        }

        @Override
        protected int tcpListenPort() {
            return CALLHOME_PORT;
        }

        @Override
        CompletionStage<TransportStack> initiate(final SshdSocketAddress local, final SshdSocketAddress remote)
                throws UnsupportedConfigurationException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }

        @Override
        CompletionStage<TransportStack> listen(final SshdSocketAddress local)
                throws UnsupportedConfigurationException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }
    }

    private static final class Server extends SSHTransportStackBootstrap {
        private final SshServerGrouping parameters;

        private Server(final TransportChannelListener listener, final SshServerGrouping parameters) {
            super(listener);
            this.parameters = requireNonNull(parameters);
        }

        @Override
        protected int tcpConnectPort() {
            return CALLHOME_PORT;
        }

        @Override
        protected int tcpListenPort() {
            return NETCONF_PORT;
        }

        @Override
        CompletionStage<TransportStack> initiate(final SshdSocketAddress local, final SshdSocketAddress remote)
                throws UnsupportedConfigurationException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }

        @Override
        CompletionStage<TransportStack> listen(final SshdSocketAddress local)
                throws UnsupportedConfigurationException {
            // FIXME: implement this
            throw new UnsupportedOperationException();
        }
    }

    public static final class ClientBuilder implements Mutable {
        private TransportChannelListener listener;
        private SshClientGrouping parameters;

        private ClientBuilder() {
            // Hidden on purpose
        }

        public @NonNull ClientBuilder setListener(final TransportChannelListener listener) {
            this.listener = requireNonNull(listener);
            return this;
        }

        public @NonNull ClientBuilder setParameters(final SshClientGrouping parameters) {
            this.parameters = requireNonNull(parameters);
            return this;
        }

        public @NonNull SSHTransportStackBootstrap build() {
            return new Client(listener, parameters);
        }
    }

    public static final class ServerBuilder implements Mutable {
        private TransportChannelListener listener;
        private SshServerGrouping parameters;

        private ServerBuilder() {
            // Hidden on purpose
        }

        public @NonNull ServerBuilder setListener(final TransportChannelListener listener) {
            this.listener = requireNonNull(listener);
            return this;
        }

        public @NonNull ServerBuilder setParameters(final SshServerGrouping parameters) {
            this.parameters = requireNonNull(parameters);
            return this;
        }

        public @NonNull SSHTransportStackBootstrap build() {
            return new Server(listener, parameters);
        }
    }

    private static final int NETCONF_PORT = 830;
    private static final int CALLHOME_PORT = 4334;

    private static final ImmutableMap<KeyExchangeAlgBase, KeyExchangeFactory> KEXS =
        ImmutableMap.<KeyExchangeAlgBase, KeyExchangeFactory>builder()
            .put(Curve25519Sha256.VALUE, null)
            .put(Curve448Sha512.VALUE, null)
            .put(DiffieHellmanGroup14Sha1.VALUE, null)
            .put(DiffieHellmanGroup14Sha256.VALUE, null)
            .put(DiffieHellmanGroup15Sha512.VALUE, null)
            .put(DiffieHellmanGroup16Sha512.VALUE, null)
            .put(DiffieHellmanGroup17Sha512.VALUE, null)
            .put(DiffieHellmanGroup18Sha512.VALUE, null)
            .put(DiffieHellmanGroup1Sha1.VALUE, null)
            .put(DiffieHellmanGroupExchangeSha1.VALUE, null)
            .put(DiffieHellmanGroupExchangeSha256.VALUE, null)
            .put(EcdhSha21284010045311.VALUE, null)
            .put(EcdhSha213132016.VALUE, null)
            .put(EcdhSha21313201.VALUE, null)
            .put(EcdhSha213132026.VALUE, null)
            .put(EcdhSha213132027.VALUE, null)
            .put(EcdhSha213132033.VALUE, null)
            .put(EcdhSha213132036.VALUE, null)
            .put(EcdhSha213132037.VALUE, null)
            .put(EcdhSha213132038.VALUE, null)
            .put(EcdhSha2Nistp256.VALUE, null)
            .put(EcdhSha2Nistp384.VALUE, null)
            .put(EcdhSha2Nistp521.VALUE, null)
            .put(EcmqvSha2.VALUE, null)
            .put(ExtInfoC.VALUE, null)
            .put(ExtInfoS.VALUE, null)
            .put(GssCurve25519Sha2561284010045311.VALUE, null)
            .put(GssCurve25519Sha25613132016.VALUE, null)
            .put(GssCurve25519Sha2561313201.VALUE, null)
            .put(GssCurve25519Sha25613132026.VALUE, null)
            .put(GssCurve25519Sha25613132027.VALUE, null)
            .put(GssCurve25519Sha25613132033.VALUE, null)
            .put(GssCurve25519Sha25613132036.VALUE, null)
            .put(GssCurve25519Sha25613132037.VALUE, null)
            .put(GssCurve25519Sha25613132038.VALUE, null)
            .put(GssCurve25519Sha256Curve25519Sha256.VALUE, null)
            .put(GssCurve25519Sha256Curve448Sha512.VALUE, null)
            .put(GssCurve25519Sha256Nistp256.VALUE, null)
            .put(GssCurve25519Sha256Nistp384.VALUE, null)
            .put(GssCurve25519Sha256Nistp521.VALUE, null)
            .put(GssCurve448Sha5121284010045311.VALUE, null)
            .put(GssCurve448Sha51213132016.VALUE, null)
            .put(GssCurve448Sha5121313201.VALUE, null)
            .put(GssCurve448Sha51213132026.VALUE, null)
            .put(GssCurve448Sha51213132027.VALUE, null)
            .put(GssCurve448Sha51213132033.VALUE, null)
            .put(GssCurve448Sha51213132036.VALUE, null)
            .put(GssCurve448Sha51213132037.VALUE, null)
            .put(GssCurve448Sha51213132038.VALUE, null)
            .put(GssCurve448Sha512Curve25519Sha256.VALUE, null)
            .put(GssCurve448Sha512Curve448Sha512.VALUE, null)
            .put(GssCurve448Sha512Nistp256.VALUE, null)
            .put(GssCurve448Sha512Nistp384.VALUE, null)
            .put(GssCurve448Sha512Nistp521.VALUE, null)
            .put(GssGexSha11284010045311.VALUE, null)
            .put(GssGexSha113132016.VALUE, null)
            .put(GssGexSha11313201.VALUE, null)
            .put(GssGexSha113132026.VALUE, null)
            .put(GssGexSha113132027.VALUE, null)
            .put(GssGexSha113132033.VALUE, null)
            .put(GssGexSha113132036.VALUE, null)
            .put(GssGexSha113132037.VALUE, null)
            .put(GssGexSha113132038.VALUE, null)
            .put(GssGexSha1Curve25519Sha256.VALUE, null)
            .put(GssGexSha1Curve448Sha512.VALUE, null)
            .put(GssGexSha1Nistp256.VALUE, null)
            .put(GssGexSha1Nistp384.VALUE, null)
            .put(GssGexSha1Nistp521.VALUE, null)
            .put(GssGroup1Sha11284010045311.VALUE, null)
            .put(GssGroup1Sha113132016.VALUE, null)
            .put(GssGroup1Sha11313201.VALUE, null)
            .put(GssGroup1Sha113132026.VALUE, null)
            .put(GssGroup1Sha113132027.VALUE, null)
            .put(GssGroup1Sha113132033.VALUE, null)
            .put(GssGroup1Sha113132036.VALUE, null)
            .put(GssGroup1Sha113132037.VALUE, null)
            .put(GssGroup1Sha113132038.VALUE, null)
            .put(GssGroup1Sha1Curve25519Sha256.VALUE, null)
            .put(GssGroup1Sha1Curve448Sha512.VALUE, null)
            .put(GssGroup1Sha1Nistp256.VALUE, null)
            .put(GssGroup1Sha1Nistp384.VALUE, null)
            .put(GssGroup1Sha1Nistp521.VALUE, null)
            .put(GssGroup14Sha11284010045311.VALUE, null)
            .put(GssGroup14Sha113132016.VALUE, null)
            .put(GssGroup14Sha11313201.VALUE, null)
            .put(GssGroup14Sha113132026.VALUE, null)
            .put(GssGroup14Sha113132027.VALUE, null)
            .put(GssGroup14Sha113132033.VALUE, null)
            .put(GssGroup14Sha113132036.VALUE, null)
            .put(GssGroup14Sha113132037.VALUE, null)
            .put(GssGroup14Sha113132038.VALUE, null)
            .put(GssGroup14Sha1Curve25519Sha256.VALUE, null)
            .put(GssGroup14Sha1Curve448Sha512.VALUE, null)
            .put(GssGroup14Sha1Nistp256.VALUE, null)
            .put(GssGroup14Sha1Nistp384.VALUE, null)
            .put(GssGroup14Sha1Nistp521.VALUE, null)
            .put(GssGroup14Sha2561284010045311.VALUE, null)
            .put(GssGroup14Sha25613132016.VALUE, null)
            .put(GssGroup14Sha2561313201.VALUE, null)
            .put(GssGroup14Sha25613132026.VALUE, null)
            .put(GssGroup14Sha25613132027.VALUE, null)
            .put(GssGroup14Sha25613132033.VALUE, null)
            .put(GssGroup14Sha25613132036.VALUE, null)
            .put(GssGroup14Sha25613132037.VALUE, null)
            .put(GssGroup14Sha25613132038.VALUE, null)
            .put(GssGroup14Sha256Curve25519Sha256.VALUE, null)
            .put(GssGroup14Sha256Curve448Sha512.VALUE, null)
            .put(GssGroup14Sha256Nistp256.VALUE, null)
            .put(GssGroup14Sha256Nistp384.VALUE, null)
            .put(GssGroup14Sha256Nistp521.VALUE, null)
            .put(GssGroup15Sha5121284010045311.VALUE, null)
            .put(GssGroup15Sha51213132016.VALUE, null)
            .put(GssGroup15Sha5121313201.VALUE, null)
            .put(GssGroup15Sha51213132026.VALUE, null)
            .put(GssGroup15Sha51213132027.VALUE, null)
            .put(GssGroup15Sha51213132033.VALUE, null)
            .put(GssGroup15Sha51213132036.VALUE, null)
            .put(GssGroup15Sha51213132037.VALUE, null)
            .put(GssGroup15Sha51213132038.VALUE, null)
            .put(GssGroup15Sha512Curve25519Sha256.VALUE, null)
            .put(GssGroup15Sha512Curve448Sha512.VALUE, null)
            .put(GssGroup15Sha512Nistp256.VALUE, null)
            .put(GssGroup15Sha512Nistp384.VALUE, null)
            .put(GssGroup15Sha512Nistp521.VALUE, null)
            .put(GssGroup16Sha5121284010045311.VALUE, null)
            .put(GssGroup16Sha51213132016.VALUE, null)
            .put(GssGroup16Sha5121313201.VALUE, null)
            .put(GssGroup16Sha51213132026.VALUE, null)
            .put(GssGroup16Sha51213132027.VALUE, null)
            .put(GssGroup16Sha51213132033.VALUE, null)
            .put(GssGroup16Sha51213132036.VALUE, null)
            .put(GssGroup16Sha51213132037.VALUE, null)
            .put(GssGroup16Sha51213132038.VALUE, null)
            .put(GssGroup16Sha512Curve25519Sha256.VALUE, null)
            .put(GssGroup16Sha512Curve448Sha512.VALUE, null)
            .put(GssGroup16Sha512Nistp256.VALUE, null)
            .put(GssGroup16Sha512Nistp384.VALUE, null)
            .put(GssGroup16Sha512Nistp521.VALUE, null)
            .put(GssGroup17Sha5121284010045311.VALUE, null)
            .put(GssGroup17Sha51213132016.VALUE, null)
            .put(GssGroup17Sha5121313201.VALUE, null)
            .put(GssGroup17Sha51213132026.VALUE, null)
            .put(GssGroup17Sha51213132027.VALUE, null)
            .put(GssGroup17Sha51213132033.VALUE, null)
            .put(GssGroup17Sha51213132036.VALUE, null)
            .put(GssGroup17Sha51213132037.VALUE, null)
            .put(GssGroup17Sha51213132038.VALUE, null)
            .put(GssGroup17Sha512Curve25519Sha256.VALUE, null)
            .put(GssGroup17Sha512Curve448Sha512.VALUE, null)
            .put(GssGroup17Sha512Nistp256.VALUE, null)
            .put(GssGroup17Sha512Nistp384.VALUE, null)
            .put(GssGroup17Sha512Nistp521.VALUE, null)
            .put(GssGroup18Sha5121284010045311.VALUE, null)
            .put(GssGroup18Sha51213132016.VALUE, null)
            .put(GssGroup18Sha5121313201.VALUE, null)
            .put(GssGroup18Sha51213132026.VALUE, null)
            .put(GssGroup18Sha51213132027.VALUE, null)
            .put(GssGroup18Sha51213132033.VALUE, null)
            .put(GssGroup18Sha51213132036.VALUE, null)
            .put(GssGroup18Sha51213132037.VALUE, null)
            .put(GssGroup18Sha51213132038.VALUE, null)
            .put(GssGroup18Sha512Curve25519Sha256.VALUE, null)
            .put(GssGroup18Sha512Curve448Sha512.VALUE, null)
            .put(GssGroup18Sha512Nistp256.VALUE, null)
            .put(GssGroup18Sha512Nistp384.VALUE, null)
            .put(GssGroup18Sha512Nistp521.VALUE, null)
            .put(GssNistp256Sha2561284010045311.VALUE, null)
            .put(GssNistp256Sha25613132016.VALUE, null)
            .put(GssNistp256Sha2561313201.VALUE, null)
            .put(GssNistp256Sha25613132026.VALUE, null)
            .put(GssNistp256Sha25613132027.VALUE, null)
            .put(GssNistp256Sha25613132033.VALUE, null)
            .put(GssNistp256Sha25613132036.VALUE, null)
            .put(GssNistp256Sha25613132037.VALUE, null)
            .put(GssNistp256Sha25613132038.VALUE, null)
            .put(GssNistp256Sha256Curve25519Sha256.VALUE, null)
            .put(GssNistp256Sha256Curve448Sha512.VALUE, null)
            .put(GssNistp256Sha256Nistp256.VALUE, null)
            .put(GssNistp256Sha256Nistp384.VALUE, null)
            .put(GssNistp256Sha256Nistp521.VALUE, null)
            .put(GssNistp384Sha3841284010045311.VALUE, null)
            .put(GssNistp384Sha38413132016.VALUE, null)
            .put(GssNistp384Sha3841313201.VALUE, null)
            .put(GssNistp384Sha38413132026.VALUE, null)
            .put(GssNistp384Sha38413132027.VALUE, null)
            .put(GssNistp384Sha38413132033.VALUE, null)
            .put(GssNistp384Sha38413132036.VALUE, null)
            .put(GssNistp384Sha38413132037.VALUE, null)
            .put(GssNistp384Sha38413132038.VALUE, null)
            .put(GssNistp384Sha384Curve25519Sha256.VALUE, null)
            .put(GssNistp384Sha384Curve448Sha512.VALUE, null)
            .put(GssNistp384Sha384Nistp256.VALUE, null)
            .put(GssNistp384Sha384Nistp384.VALUE, null)
            .put(GssNistp384Sha384Nistp521.VALUE, null)
            .put(GssNistp521Sha5121284010045311.VALUE, null)
            .put(GssNistp521Sha51213132016.VALUE, null)
            .put(GssNistp521Sha5121313201.VALUE, null)
            .put(GssNistp521Sha51213132026.VALUE, null)
            .put(GssNistp521Sha51213132027.VALUE, null)
            .put(GssNistp521Sha51213132033.VALUE, null)
            .put(GssNistp521Sha51213132036.VALUE, null)
            .put(GssNistp521Sha51213132037.VALUE, null)
            .put(GssNistp521Sha51213132038.VALUE, null)
            .put(GssNistp521Sha512Curve25519Sha256.VALUE, null)
            .put(GssNistp521Sha512Curve448Sha512.VALUE, null)
            .put(GssNistp521Sha512Nistp256.VALUE, null)
            .put(GssNistp521Sha512Nistp384.VALUE, null)
            .put(GssNistp521Sha512Nistp521.VALUE, null)
            .put(Rsa1024Sha1.VALUE, null)
            .put(Rsa2048Sha256.VALUE, null)
            .build();

    private static final ImmutableMap<MacAlgBase, MacFactory> MACS = ImmutableMap.<MacAlgBase, MacFactory>builder()
        .put(HmacMd5.VALUE, BuiltinMacs.hmacmd5)
        .put(HmacMd596.VALUE, BuiltinMacs.hmacmd596)
        .put(HmacSha1.VALUE, BuiltinMacs.hmacsha1)
        .put(HmacSha196.VALUE, BuiltinMacs.hmacsha196)
        .put(HmacSha2256.VALUE, BuiltinMacs.hmacsha256)
        .put(HmacSha2512.VALUE, BuiltinMacs.hmacsha512)
        // FIXME: resolve by name?!
        // FIXME: AeadAes128Gcm.VALUE
        // FIXME: AeadAes256Gcm.VALUE
        // FIXME: None.VALUE
        // FIXME openssh ETM extensions
        .build();

    SSHTransportStackBootstrap(final TransportChannelListener listener) {
        super(listener);
    }

    public static @NonNull ClientBuilder clientBuilder() {
        return new ClientBuilder();
    }

    @Override
    public final CompletionStage<TransportStack> initiate(final TcpClientGrouping connectParams)
            throws UnsupportedConfigurationException {
        // FIXME: move some of this to superclass
        final var remoteHost = require(connectParams, TcpClientGrouping::getRemoteAddress, "remote-address");
        final var remotePort = require(connectParams, TcpClientGrouping::getRemotePort, "remote-port");
        final var remoteAddr = remoteHost.getIpAddress();

        return initiate(addressOf(connectParams.getLocalAddress(), connectParams.getLocalPort(), 0),
            remoteAddr != null ? addressOf(remoteAddr, remotePort, tcpConnectPort())
                : new SshdSocketAddress(
                    require(remoteHost, Host::getDomainName, "remote-address/domain-name").getValue(),
                    portNumber(remotePort, tcpConnectPort())));
    }

    abstract @NonNull CompletionStage<TransportStack> initiate(SshdSocketAddress local, SshdSocketAddress remote)
        throws UnsupportedConfigurationException;

    @Override
    public final CompletionStage<TransportStack> listen(final TcpServerGrouping listenParams)
            throws UnsupportedConfigurationException {
        // FIXME: move some of this to superclass

        return listen(addressOf(require(listenParams, TcpServerGrouping::getLocalAddress, "local-address"),
            listenParams.getLocalPort(), tcpListenPort()));
    }

    abstract @NonNull CompletionStage<TransportStack> listen(SshdSocketAddress local)
        throws UnsupportedConfigurationException;

    private static SshdSocketAddress addressOf(final @Nullable IpAddress address,
            final @Nullable PortNumber port, final int defaultPort) {
        final int portNum = portNumber(port, defaultPort);
        return address == null ?  new SshdSocketAddress(portNum)
            : new SshdSocketAddress(new InetSocketAddress(IetfInetUtil.INSTANCE.inetAddressFor(address), portNum));
    }

    private static void setTransportParams(final @NonNull BaseBuilder<?, ?> builder,
            final @Nullable TransportParamsGrouping params) throws UnsupportedConfigurationException {
        if (params != null) {
            setEncryption(builder, params.getEncryption());
            setHostKey(builder, params.getHostKey());
            setKeyExchange(builder, params.getKeyExchange());
            setMac(builder, params.getMac());
        }
    }

    private static void setEncryption(final BaseBuilder<?, ?> baseBuilder, final Encryption encryption)
            throws UnsupportedConfigurationException {
        if (encryption == null) {
            return;
        }

        // FIXME: implement this
    }

    private static void setHostKey(final BaseBuilder<?, ?> baseBuilder, final HostKey hostKey)
            throws UnsupportedConfigurationException {
        if (hostKey == null) {
            return;
        }

        // FIXME: implement this
    }

    private static void setKeyExchange(final BaseBuilder<?, ?> baseBuilder, final KeyExchange keyExchange)
            throws UnsupportedConfigurationException {
        if (keyExchange != null) {
            final var kexAlg = keyExchange.getKeyExchangeAlg();
            if (kexAlg != null && !kexAlg.isEmpty()) {
                // FIXME: implement this
                baseBuilder.keyExchangeFactories(createKexFactories(kexAlg));
            }
        }
    }

    private static List<KeyExchangeFactory> createKexFactories(final List<KeyExchangeAlgBase> kexAlg) {
        // FIXME: cache these
        final var builder = ImmutableList.<KeyExchangeFactory>builderWithExpectedSize(kexAlg.size());
        for (var alg : kexAlg) {
            builder.add(kexFactoryOf(alg));
        }
        return builder.build();
    }



    private static KeyExchangeFactory kexFactoryOf(final KeyExchangeAlgBase alg) {
        // TODO Auto-generated method stub
        return null;
    }

    private static void setMac(final BaseBuilder<?, ?> baseBuilder, final Mac mac)
            throws UnsupportedConfigurationException {
        if (mac != null) {
            final var macAlg = mac.getMacAlg();
            if (macAlg != null && !macAlg.isEmpty()) {
                baseBuilder.macFactories(createMacFactories(macAlg));
            }
        }
    }

    private static List<NamedFactory<org.opendaylight.netconf.shaded.sshd.common.mac.Mac>> createMacFactories(
            final List<MacAlgBase> macAlg) throws UnsupportedConfigurationException {
        // FIXME: cache these
        final var builder = ImmutableList.<NamedFactory<org.opendaylight.netconf.shaded.sshd.common.mac.Mac>>
            builderWithExpectedSize(macAlg.size());
        for (var alg : macAlg) {
            builder.add(macFactoryOf(alg));
        }
        return builder.build();
    }

    private static @NonNull MacFactory macFactoryOf(final MacAlgBase alg) throws UnsupportedConfigurationException {
        final var ret = MACS.get(alg);
        if (ret == null) {
            throw new UnsupportedConfigurationException("Unsupported MAC algorithm " + alg);
        }
        return ret;
    }
}
