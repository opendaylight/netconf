/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http.rfc6415;

import com.google.common.annotations.Beta;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import java.net.URI;
import java.util.stream.Stream;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.common.XMLNamespace;

/**
 * The concepts of an
 * <a href="https://docs.oasis-open.org/xri/xrd/v1.0/xrd-1.0.html">Extensible Resource Descriptor (XRD)</a>.
 */
@Beta
@NonNullByDefault
public interface XRD {
    /**
     * The namespace reference to XRD.
     */
    XMLNamespace NAMESPACE = XMLNamespace.of("http://docs.oasis-open.org/ns/xri/xrd-1.0");
    /**
     * The <a href="http://docs.oasis-open.org/xri/xrd/v1.0/os/xrd-1.0-os.xsd">XML Schema file for XRD 1.0</a>.
     */
    ByteSource XSD = Resources.asByteSource(XRD.class.getResource("/xrd-1.0-os.xsd"));

    /**
     * Return the stream {@link Link}s ordered by their {@link Link#rel()} attribute.
     *
     * @return the stream {@link Link}s
     */
    Stream<? extends Link> links();

    /**
     * Lookup the link with specified {@code rel} attribute.
     *
     * @param rel attribute to look for
     * @return a {@link Link}, or {@code null} no link was found
     * @throws NullPointerException if {@code rel} is {@code null}
     */
    @Nullable Link lookupLink(URI rel);
}
