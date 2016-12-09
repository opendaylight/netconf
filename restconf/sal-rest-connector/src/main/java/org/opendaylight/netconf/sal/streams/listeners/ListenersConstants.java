/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 *
 */
package org.opendaylight.netconf.sal.streams.listeners;

import java.text.SimpleDateFormat;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;

/**
 * @author ary
 *
 */
class ListenersConstants {

    static final DocumentBuilderFactory DBF = DocumentBuilderFactory.newInstance();
    static final TransformerFactory FACTORY = TransformerFactory.newInstance();
    static final Pattern RFC3339_PATTERN = Pattern.compile("(\\d\\d)(\\d\\d)$");

    static final SimpleDateFormat RFC3339 = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssZ");
}
