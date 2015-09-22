"""Library hiding details of restconf authentication.

Suitable for performance tests against different
restconf authentication methods.
Type of authentication is currently determined by "scope" value.

This library does not rely on RobotFramework libraries,
so it is suitable for running from wide range of VMs.
Requirements: Basic Python installation,
which should include "json" and "requests" Python modules.

*_Using_Session keywords take the same kwargs as requests.Session.request,
but instead of method and URL, they take "session" created by
Init_Session keyword and URI (without "/restconf/").

Due to performance of TCP on some systems, two session strategies are available.
reuse=True reuses the same requests.Session, which possibly means
the library tries to re-use the same source TCP port.
This conserves resources (TCP ports available), but it may lead
to a significant performance hit (as in 10 times slower).
reuse=False closes every requests.Session object, presumably
causing the new session to take another TCP port.
This has good performance, but may perhaps lead to port starvation in some cases.

TODO: Put "RESTCONF" to more places,
as URIs not starting with /restconf/ are not supported yet.
"""

# Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html

__author__ = "Vratko Polak"
__copyright__ = "Copyright(c) 2015, Cisco Systems, Inc."
__license__ = "Eclipse Public License v1.0"
__email__ = "vrpolak@cisco.com"


import json
import requests


#
# Karaf Keyword definitions.
#
def Init_Session(ip, username, password, scope, reuse=True, port="8181"):
    """Robot keyword, return opaque session object, which handles authentication automatically."""
    if scope:
        if reuse:
            return _TokenReusingSession(ip, username, password, scope, port=port)
        else:
            return _TokenClosingSession(ip, username, password, scope, port=port)
    else:
        if reuse:
            return _BasicReusingSession(ip, username, password, port=port)
        else:
            return _BasicClosingSession(ip, username, password, port=port)


def Get_Using_Session(session, uri, **kwargs):
    """Robot keyword, perform GET operation using given opaque session object."""
    return session.robust_method("GET", uri, **kwargs)


def Post_Using_Session(session, uri, **kwargs):
    """Robot keyword, perform POST operation using given opaque session object."""
    return session.robust_method("POST", uri, **kwargs)


def Put_Using_Session(session, uri, **kwargs):
    """Robot keyword, perform PUT operation using given opaque session object."""
    return session.robust_method("PUT", uri, **kwargs)


def Delete_Using_Session(session, uri, **kwargs):
    """Robot keyword, perform DELETE operation using given opaque session object."""
    return session.robust_method("DELETE", uri, **kwargs)


#
# Private classes.
#
class _BasicReusingSession(object):
    """Handling of restconf requests using persistent session and basic http authentication."""

    def __init__(self, ip, username="", password="", port="8181"):
        """Initialize session using hardcoded text data, remember credentials."""
        self.rest_prefix = "http://" + ip + ":" + port + "/restconf/"
        self.session = requests.Session()
        if username:
            self.session.auth = (username, password)  # May work with non-string values
        else:
            self.session.auth = None  # Supports "no authentication mode" as in odl-restconf-noauth

    def robust_method(self, method, uri, **kwargs):
        """Try method once using session credentials. Return last response."""
        return self.session.request(method, self.rest_prefix + uri, **kwargs)
        # TODO: Do we want to fix URI at __init__ just to avoid string concat?


class _BasicClosingSession(object):
    """Handling of restconf requests using one-time sessions and basic http authentication."""

    def __init__(self, ip, username="", password="", port="8181"):
        """Prepare session initialization data using hardcoded text, remember credentials."""
        self.rest_prefix = "http://" + ip + ":" + port + "/restconf/"
        if username:
            self.auth = (username, password)  # May work with non-string values
        else:
            self.auth = None  # Supports "no authentication mode" as in odl-restconf-noauth
        self.session = None

    def robust_method(self, method, uri, **kwargs):
        """Create new session, send method using remembered credentials. Return response"""
        if self.session:
            # The session object may still keep binding a TCP port here.
            # As caller has finished processing previous response,
            # we can explicitly close the old session to free resources
            # before creating new session.
            self.session.close()
        self.session = requests.Session()
        self.session.auth = self.auth
        return self.session.request(method, self.rest_prefix + uri, **kwargs)


class _TokenReusingSession(object):
    """Handling of restconf requests using token-based authentication, one session per token."""

    def __init__(self, ip, username, password, scope, port="8181"):
        """Initialize session using hardcoded text data."""
        self.auth_url = "http://" + ip + ":" + port + "/oauth2/token"
        self.rest_prefix = "http://" + ip + ":" + port + "/restconf/"
        self.auth_data = "grant_type=password&username=" + username
        self.auth_data += "&password=" + password + "&scope=" + scope
        self.auth_header = {"Content-Type": "application/x-www-form-urlencoded"}
        self.session = None
        self.token = None
        self.refresh_token()

    def refresh_token(self):
        """Reset session, invoke call to get token, parse it and remember."""
        if self.session:
            self.session.close()
        self.session = requests.Session()
        resp = self.session.post(self.auth_url, data=self.auth_data, headers=self.auth_header)
        resp_obj = json.loads(resp.text)
        try:
            token = resp_obj["access_token"]
        except KeyError:
            raise RuntimeError("Parse failed: " + resp.text)
        self.token = token
        # TODO: Use logging so that callers could see token refreshes.
        # print "DEBUG: token:", token
        # We keep self.session to use for the following restconf requests.

    def oneshot_method(self, method, uri, **kwargs):
        """Return response of request of given method to given uri (without restconf/)."""
        # Token needs to be merged into headers.
        authed_headers = kwargs.get("headers", {})
        authed_headers["Authorization"] = "Bearer " + self.token
        authed_kwargs = dict(kwargs)  # shallow copy
        authed_kwargs["headers"] = authed_headers
        return self.session.request(method, self.rest_prefix + uri, **authed_kwargs)

    def robust_method(self, method, uri, **kwargs):
        """Try method once; upon 401, refresh token and retry once. Return last response."""
        resp = self.oneshot_method(method, uri, **kwargs)
        if resp.status_code != 401:
            return resp
        self.refresh_token()
        return self.oneshot_method(method, uri, **kwargs)


class _TokenClosingSession(object):
    """Handling of restconf requests using token-based authentication, one session per request."""

    def __init__(self, ip, username, password, scope, port="8181"):
        """Prepare session initialization data using hardcoded text."""
        self.auth_url = "http://" + ip + ":" + port + "/oauth2/token"
        self.rest_prefix = "http://" + ip + ":" + port + "/restconf/"
        self.auth_data = "grant_type=password&username=" + username
        self.auth_data += "&password=" + password + "&scope=" + scope
        self.auth_header = {"Content-Type": "application/x-www-form-urlencoded"}
        self.session = None
        self.token = None
        self.refresh_token()

    def refresh_token(self):
        """Reset session, invoke call to get token, parse it and remember."""
        if self.session:
            self.session.close()
        self.session = requests.Session()
        resp = self.session.post(self.auth_url, data=self.auth_data, headers=self.auth_header)
        resp_obj = json.loads(resp.text)
        try:
            token = resp_obj["access_token"]
        except KeyError:
            raise RuntimeError("Parse failed: " + resp.text)
        self.token = token
        # TODO: Use logging so that callers could see token refreshes.
        # print "DEBUG: token:", token
        # We keep self.session to use for the following restconf requests.

    def oneshot_method(self, method, uri, **kwargs):
        """Reset session, return response of request of given method to given uri (without restconf/)."""
        # This assumes self.session was already initialized.
        self.session.close()
        self.session = requests.Session()
        # Token needs to be merged into headers.
        authed_headers = kwargs.get("headers", {})
        authed_headers["Authorization"] = "Bearer " + self.token
        authed_kwargs = dict(kwargs)  # shallow copy
        authed_kwargs["headers"] = authed_headers
        return self.session.request(method, self.rest_prefix + uri, **authed_kwargs)

    def robust_method(self, method, uri, **kwargs):
        """Try method once; upon 401, refresh token and retry once. Return last response."""
        resp = self.oneshot_method(method, uri, **kwargs)
        if resp.status_code != 401:
            return resp
        self.refresh_token()
        return self.oneshot_method(method, uri, **kwargs)
