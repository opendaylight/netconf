/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.config;

import java.util.Objects;

public class YangResource {

    private final String moduleName;
    private final String revision;
    private final String resourcePath;

    public YangResource(String moduleName, String revision, String resourcePath) {
        this.moduleName = moduleName;
        this.revision = revision;
        this.resourcePath = resourcePath;
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getRevision() {
        return revision;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        YangResource that = (YangResource) object;
        return Objects.equals(moduleName, that.moduleName)
                && Objects.equals(revision, that.revision)
                && Objects.equals(resourcePath, that.resourcePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(moduleName, revision, resourcePath);
    }

}
