#default host is localhost, use --host [ip] as the python argument to change the host ip
#example to run the script
#python dtxbenchmark_test.py --host 127.0.0.1 --logicalTxType NETCONF --loop 40

import argparse
import requests
import json
import csv

parser = argparse.ArgumentParser(description = 'benchmarktest for Dtx')

#Host Config
parser.add_argument("--host", default = "localhost", help = "The IP of the host running the test application")
parser.add_argument("--port", default = 8181, type = int, help = "The port number of the host")

#Test Parameters
parser.add_argument("--logicalTxType", choices = ["DATASTORE", "NETCONF"], nargs = "+", default = ["DATASTORE", "NETCONF"],
                    help = "The transaction type of the test")
parser.add_argument("--operation", choices = ["PUT", "MERGE", "DELETE"], nargs = "+", default = ["PUT", "MERGE", "DELETE"],
                    help = "The operation type of the transaction")
parser.add_argument("--putsPerTx", default = [1, 10, 100, 1000, 5000, 10000, 100000],type = int, nargs = "+",
                    help = "Number of operations per transaction")
parser.add_argument("--putsPerTxNetconf", default = [1, 3, 6, 9, 12, 15, 18],type = int, nargs = "+",
                    help = "Number of operations per transaction")
parser.add_argument("--outerList", default = 1000, type = int, help = "The size of outer Elements")
parser.add_argument("--innerList", default = 100, type = int, help = "The size of inner Elements")
parser.add_argument("--loop", default =1, type = int, help = "test time")
args = parser.parse_args()

#Base url for the test
BASE_URL = "http://%s:%d/restconf/" % (args.host, args.port)

#Test the performance of DTx
def benchmark_test(logicalTxType, operation, putsPerTx, loop, outerList = 0, innerList = 0):
    """
    Send a test request to DTx it model to start performance test
    :param logicalTxType: DATASTORE, NETCONF
    :param operaton:PUT, MERGE, DELETE
    :param putsPerTx: number of the operation per transaction
    :param loop: test time
    :param outerList:size of outer Elements
    :param innerList:size of inner Elements
    :return: execution time of the test
    """
    url = BASE_URL + "operations/distributed-tx-it-model:benchmark-test"
    postheaders =  {'content-type': 'application/json', 'Accept': 'application/json'}

    test_request_template = '''{
        "input":{
            "logicalTxType" : "%s",
            "operation" : "%s",
            "putsPerTx" : "%d",
            "outerList" : "%d",
            "innerList" :"%d",
            "loop" : "%d"
        }
    }'''
    data = test_request_template % (logicalTxType, operation, putsPerTx, outerList, innerList, loop)
    r = requests.post(url, data, headers = postheaders, stream = False, auth = ('admin', 'admin'))
    result = {u'http-status' : r.status_code}
    if r.status_code == 200:
        result = dict(result.items() + json.loads(r.content)["output"].items())
    else:
        print 'Error %s, %s' % (r.status_code, r.content)
    return result;

#Get test parameter from the argument
logicalTxTypes = args.logicalTxType
operations = args.operation
putsPerTxs = args.putsPerTx
putsPerTxNetconfs = args.putsPerTxNetconf
loop = args.loop

#result.csv is used to store the test result
f = open('result.csv', 'w')
try:
    writer = csv.writer(f)
    #Iterate over all the tx types: DATASTORE and NETCONF
    #get the performance results and write to the result.csv file
    print "Performance test will take around 35 mins to finish"
    for logicalTxType in logicalTxTypes:
        print "#############################################################################"
        print "DTx %s performance test begins" % logicalTxType
        print "#############################################################################"
        if logicalTxType == "DATASTORE":
            for operation in operations:
                print "*****************************************************************************"
                print "Operation : %s" % operation
                print "*****************************************************************************"
                writer.writerow((('%s' % logicalTxType), ('%s' % operation),
                                 'putsPerTx', 'outerList', 'innerList', 'dbExecTime(us)', 'dtxSyncExecTime(us)', 'dbSubmitOk', 'dtxSyncSubmitOk'))
                for putsPerTx in putsPerTxs:
                    if putsPerTx < 1000:
                        #When putsPerTx is small the input should be small or it will take a long time to finish the test
                        outerList = 100
                        innerList = 10
                    else:
                        outerList = args.outerList
                        innerList = args.innerList
                    print "-----------------------------------------------------------------------------"
                    print "Testing for OuterList : %d, InnerList : %d, putsPerTx : %d" % (outerList, innerList, putsPerTx)

                    result = benchmark_test(logicalTxType, operation, putsPerTx, loop, outerList, innerList)
                    writer.writerow(('', '', ('%d' % putsPerTx), ('%d' % outerList), ('%d' % innerList), ('%d' %  result["execTime"]),
                                     ('%d' % result["dtxSyncExecTime"]), ('%d' % result['dbOk']), ('%d' % result['dTxSyncOk'])))

                    print "dbExecTime : %d, dtxSyncTime : %d, dbSubmitOk : %d, dtxSyncSubmitOk : %d " % (
                        result["execTime"], result["dtxSyncExecTime"], result['dbOk'], result['dTxSyncOk'])
        else:
            loop = 40
            for operation in operations:
                print "*****************************************************************************"
                print "Operation : %s" % operation
                print "*****************************************************************************"
                writer.writerow((('%s' % logicalTxType), ('%s' % operation),
                                 'putsPerTx','ExecTime(us)','dtxSyncExecTime(us)','dtxAsyncExecTime(us)','dbSubmitOk','dtxSyncSubmitOk','dtxAsyncSubmitOk'))
                for putsPerTx in putsPerTxNetconfs:
                    print "-----------------------------------------------------------------------------"
                    print "Testing for loop:%d, putsPerTx : %d" % (loop, putsPerTx)

                    result = benchmark_test(logicalTxType, operation, putsPerTx, loop)
                    writer.writerow(('', '', ('%d' % putsPerTx), ('%d' %  result["execTime"]),('%d' % result["dtxSyncExecTime"])
                                     , ('%d' % result['dtxAsyncExecTime']),('%d' % result['dbOk']), ('%d' % result['dTxSyncOk']),
                                     ('%d' % result['dTxAsyncOk'])))

                    print "dbExecTime : %d, dtxSyncTime : %d, dTxAsyncTime : %d, dbSubmitOk : %d, dtxSyncSubmitOk : %d, dtxAsyncSubmitOk : %d " % (
                        result["execTime"], result["dtxSyncExecTime"], result["dtxAsyncExecTime"], result['dbOk'], result['dTxSyncOk'], result["dTxAsyncOk"])

        print "#############################################################################"
        print "DTx %s performance test ends" % logicalTxType
        print "#############################################################################"
finally:
    f.close()
