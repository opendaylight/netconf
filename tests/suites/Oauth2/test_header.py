import json
import time

import pytest
import requests

TOPOLOGY_URL = "http://localhost:8181/rests/data/network-topology:network-topology?content=config"
KEY_STORES_URL = "http://localhost:8181/rests/data/aaa-cert-mdsal:key-stores?content=config"

ADMIN_USER_NAME = "aadmin"
ADMIN_ROLE = "admin"
NON_ADMIN_USER_NAME = "bbasic"
NON_ADMIN_ROLE = "non-admin"

def request_endpoint(user_name, user_role, restconf_url):
    headers = dict()
    if user_name:
        headers["X-Forwarded-User"] = user_name
    if user_role:
        headers["X-Forwarded-Groups"] = user_role
    response = requests.get(restconf_url, headers=headers)
    
    return response

@pytest.mark.parametrize(
        "user_name, user_role, endpoint, expected_status_code",
        [
            (ADMIN_USER_NAME, ADMIN_ROLE, KEY_STORES_URL, 200),
            (ADMIN_USER_NAME, ADMIN_ROLE, TOPOLOGY_URL, 200),
            (NON_ADMIN_USER_NAME, NON_ADMIN_ROLE, KEY_STORES_URL, 403),
            (NON_ADMIN_USER_NAME, NON_ADMIN_ROLE, TOPOLOGY_URL, 200),
            (None, ADMIN_ROLE, KEY_STORES_URL, 401),
            (ADMIN_USER_NAME, None, KEY_STORES_URL, 401),
        ]
)
def test_header(user_name, user_role, endpoint, expected_status_code):
    response = request_endpoint(
        user_name,
        user_role,
        endpoint
    )
    assert response.status_code == expected_status_code