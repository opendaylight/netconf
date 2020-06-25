/*
 * Copyright (c) 2020 Pantheon.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.formatters;

import javax.xml.xpath.XPathExpressionException;

public interface EventFormatterFactory<T> {

    EventFormatter<T> getFormatter();

    EventFormatter<T> getFormatter(String xpathFilter) throws XPathExpressionException;
}
