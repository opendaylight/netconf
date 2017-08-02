/*
 * Copyright (c) 2017 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl.jmx;

public class Config {
    private Delete delete;

    private Post post;

    private Get get;

    private Put put;

    public Delete getDelete() {
        return delete;
    }

    public void setDelete(Delete delete) {
        this.delete = delete;
    }

    public Post getPost() {
        return post;
    }

    public void setPost(Post post) {
        this.post = post;
    }

    public Get getGet() {
        return get;
    }

    public void setGet(Get get) {
        this.get = get;
    }

    public Put getPut() {
        return put;
    }

    public void setPut(Put put) {
        this.put = put;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(delete, post, get, put);

    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final Config that = (Config) obj;
        if (!java.util.Objects.equals(delete, that.delete)) {
            return false;
        }

        if (!java.util.Objects.equals(post, that.post)) {
            return false;
        }

        if (!java.util.Objects.equals(get, that.get)) {
            return false;
        }

        return java.util.Objects.equals(put, that.put);

    }
}
