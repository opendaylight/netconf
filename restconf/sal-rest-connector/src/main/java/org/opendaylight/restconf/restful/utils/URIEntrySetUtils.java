/*
 * Copyright (c) 2017 Inocybe Technologies, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.restful.utils;

import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;

import javax.ws.rs.core.UriInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class URIEntrySetUtils {

    private HashMap<String, EntrySetValue> entriesSet = new HashMap<>();

    public URIEntrySetUtils(Set<String> keys, final UriInfo uriInfo) {
        for (final String keyName: keys) {
            entriesSet.put(keyName, new EntrySetValue());
        }

        for (final Map.Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
            if(keys.contains(entry.getKey())) {
                EntrySetValue entrySetValue = entriesSet.get(entry.getKey());
                if(!entrySetValue.isKeyUsed()) {
                    entrySetValue.setKeyUsed(true);
                    entrySetValue.setKeyValue(entry.getValue().iterator().next());
                    entriesSet.put(entry.getKey(), entrySetValue);
                } else {
                    throw new RestconfDocumentedException(entry.getKey().substring(0, 1).toUpperCase() +
                            entry.getKey().substring(1) + "parameter can be used only once.");
                }
            } else {
                throw new RestconfDocumentedException("Bad parameter for post: " + entry.getKey());
            }
        }
    }

    public HashMap<String, EntrySetValue> getEntriesSet() {
        return entriesSet;
    }
}