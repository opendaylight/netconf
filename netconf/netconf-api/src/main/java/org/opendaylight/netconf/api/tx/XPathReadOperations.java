/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.tx;

import com.google.common.util.concurrent.FluentFuture;
import java.util.Optional;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.xpath.api.YangExpr;

/**
 * Grouped operations used for reading of data from data-store using XPath expressions. XML Path Language (XPath)
 * Version 1.0 is used.
 *
 * @see <a href="https://www.w3.org/TR/1999/REC-xpath-19991116/">XPath 1.0 specification</a>
 * @see <a href="https://tools.ietf.org/html/rfc6241#section-8.9">Using of XPath filters in NETCONF</a>
 */
public interface XPathReadOperations {

    /**
     * Reading of data from selected data-store using XPath filter.
     *
     * @param store Logical data-store from which data is read.
     * @param xpath XPath filter used for selection of read data.
     * @return Asynchronous completion token with read data or {@link Optional#empty()}.
     */
    FluentFuture<Optional<NormalizedNode<?, ?>>> read(LogicalDatastoreType store, YangExpr xpath);

    /**
     * Checking whether data on provided XPath exists.
     *
     * @param store Logical data-store from which data is read.
     * @param xpath XPath filter used for selection of read data.
     * @return Asynchronous completion token with data existence statement.
     */
    FluentFuture<Boolean> exists(LogicalDatastoreType store, YangExpr xpath);
}