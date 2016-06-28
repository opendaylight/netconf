*** Settings ***
Suite Setup
Library           RequestsLibrary
Library           Collections

*** Variables ***
@{auth}           admin    admin
${ip}             172.27.136.49
${port}           8181
@{operations}     PUT    MERGE    DELETE

*** Test Cases ***
datastore test case
    log    DTx DataStore Normal Test
    : FOR    ${operation}    IN    @{operations}
    \    log    "DTx DataStore ${operation} test"
    \    DTx DataStore Test    ${operation}    10    10    10
    log    DTx DataStore rollback on failure test
    : FOR    ${operation}    IN    @{operations}
    \    log    "DTx DataStore rollback on failure ${operation} test"
    \    DTx DataStore Test    ${operation}    10    10    10    ROLLBACK-ON-FAILURE
    log    DTx DataStore rollback function test
    : FOR    ${operation}    IN    @{operations}
    \    log    "DTX DataStore rollback function ${operation} test"
    \    DTx DataStore Test    ${operation}

mixedprovider test case
    log    MixedProvider normal test
    : FOR    ${operation}    IN    @{operations}
    \    log    Mixed provider normal ${operation} test
    \    DTx MixedProvider test    ${operation}
    log    MIxedProvider rollback on failure test
    : FOR    ${operation}    IN    @{operations}
    \    log    Mixed provider rollback on failure ${operation} test
    \    DTx MixedProvider test    ${operation}    1    4    ROLLBACK-ON-FAILURE
    log    MixedProvider rollback function test
    : FOR    ${operation}    IN    @{operations}
    \    log    Mixed provider rollback function ${operation} test
    \    DTx MixedProvider Test    ${operation}    1    4    ROLLBACK

netconf test case
    log    netconf normal test
    : FOR    ${operation}    IN    @{operations}
    \    log    Netconf normal ${operation} test
    \    DTx Netconf Test    ${operation}
    log    netconf rollback on failure test
    : FOR    ${operation}    IN    @{operations}
    \    log    Netconf rollback on failure ${operation} test
    \    DTx Netconf Test    ${operation}    1    4    ROLLBACK-ON-FAILURE
    log    netconf rollback function test
    : FOR    ${operation}    IN    @{operations}
    \    log    Netconf rollback function ${operation} test
    \    DTx Netconf Test    ${operation}    1    4    ROLLBACK

*** Keywords ***
DTx DataStore Test
    [Arguments]    ${operation}=PUT    ${outerList}=10    ${innerList}=10    ${putPerTx}=1    ${type}=NORMAL
    [Documentation]    This keyword is used to do the datastore test for DTx
    ${postheader}    Create Dictionary    content-type=application/json    Accept=application/json
    ${url}    Set Variable    http://${ip}:${port}/restconf/operations/distributed-tx-it-model:datastore-test
    ${Input}    Set Variable    {"input":{"operation":${operation},"putsPerTx":${putPerTx},"outerList":${outerList},"innerList":${innerList},"type":${type}}}
    Create Session    DatastoreTestSession    ${url}    auth=@{auth}
    ${resp}    Post Request    DatastoreTestSession    /post    data=${input}    headers=${postheader}
    Should Be Equal As Strings    ${resp.status_code}    200
    Should Contain    ${resp.content}    OK

DTx MixedProvider Test
    [Arguments]    ${operation}=PUT    ${putsPerTx}=1    ${numberOfTxs}=4    ${type}=NORMAL
    [Documentation]    This keyword is used to do the mixedProvider Test for DTx
    ${postheader}    Create Dictionary    content-type=application/json    Accept=application/json
    ${url}    Set Variable    http://${ip}:${port}/restconf/operations/distributed-tx-it-model:mixed-provider-test
    ${input}    Set Variable    {"input":{"operation":${operation},"putsPerTx":${putsPerTx},"numberOfTxs":${numberOfTxs},"type":${type}}}
    Create Session    MixedProvidertestSession    ${url}    auth=@{auth}
    ${resp}    Post Request    MixedProviderTestSession    /post    data=${input}    headers=${postheader}
    log    ${resp.content}
    Should Be Equal As Strings    ${resp.status_code}    200
    Should Contain    ${resp.content}    OK

DTx Netconf Test
    [Arguments]    ${operation}=PUT    ${putsPerTx}=1    ${numberOfTxs}=4    ${type}=NORMAL
    [Documentation]    This keyword is used to do the netconf Test for DTx
    ${postheader}    Create Dictionary    content-type=application/json    Accept=application/json
    ${url}    Set Variable    http://${ip}:${port}/restconf/operations/distributed-tx-it-model:netconf-test
    ${input}    Set Variable    {"input":{"operation":${operation},"putsPerTx":${putsPerTx},"numberOfTxs":${numberOfTxs},"type":${type}}}
    Create Session    NetconfTestSession    ${url}    auth=@{auth}
    ${resp}    Post Request    NetconfTestSession    /post    data=${input}    headers=${postheader}
    log    ${resp.content}
    Should Be Equal As Strings    ${resp.status_code}    200
    Should Contain    ${resp.content}    OK
