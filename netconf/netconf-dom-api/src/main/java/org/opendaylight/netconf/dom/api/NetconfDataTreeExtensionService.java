/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dom.api;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMServiceExtension;
import org.opendaylight.netconf.xpath.NetconfXPathContext;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * Interface for extended base and additional operations for Netconf. According to RFC-6241.
 */
public interface NetconfDataTreeExtensionService
        extends DOMServiceExtension<NetconfDataTreeService, NetconfDataTreeExtensionService> {

    /**
     * The &lt;get&gt; operation via XPath - must be supported with XPath capability.
     * Retrieve running configuration and device state information.
     *
     * @param xpathContext get operation with use of the XPath
     * @return result of &lt;get&gt; operation
     */
    ListenableFuture<Optional<NormalizedNode<?, ?>>> get(@NonNull NetconfXPathContext xpathContext);

    /**
     * The &lt;get-config&gt; operation via XPath - must be supported with XPath
     * capability. Retrieve all or part of a specified configuration datastore.
     *
     * @param xpathContext get-config operation with use of the XPath
     * @return result of &lt;get-config&gt; operation
     */
    ListenableFuture<Optional<NormalizedNode<?, ?>>> getConfig(@NonNull NetconfXPathContext xpathContext);
}
