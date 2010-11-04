#!/usr/bin/python
# Copyright (c) 2009 Las Cumbres Observatory (www.lcogt.net)
# Copyright (c) 2010 Jan Dittberner
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
'''
service_test.py - unit tests for the protobuf.service module.

Authors: Zach Walker (zwalker@lcogt.net)
         Jan Dittberner (jan@dittberner.info)

Nov 2009, Nov 2010
'''

# Standard library imports
import unittest
import sys
import threading
from time import sleep

# Add protobuf module to path
sys.path.append('../../main')

# Import the class to test
import protobuf.socketrpc.service as service
from protobuf.socketrpc.error import RpcError
from protobuf.socketrpc.channel import SocketRpcChannel
import protobuf.socketrpc.server as server
import fake

# Import the protoc generated test module
import protobuf.socketrpc.rpc_pb2 as rpc_pb2
import test_pb2

success_port = 63636
fail_port = 63637
host = 'localhost'


class ServerThread(threading.Thread):
    '''Singleton class for starting a server thread'''

    # Singleton instance
    success_instance = None
    fail_instance = None

    def __init__(self, port, exception=None):
        threading.Thread.__init__(self)
        if exception is None:
            self.test_service = fake.TestServiceImpl()
        else:
            self.test_service = fake.TestServiceImpl(exception=exception)
        self.server = server.SocketRpcServer(port)
        self.server.registerService(self.test_service)
        self.setDaemon(True)

    @staticmethod
    def start_server(success_port, fail_port, exception):
        '''Start the singleton instance if not already running
           Will not exit until the process ends
        '''

        if ServerThread.success_instance == None:
            ServerThread.success_instance = ServerThread(success_port, None)
            ServerThread.success_instance.start()
        if ServerThread.fail_instance == None:
            ServerThread.fail_instance = ServerThread(fail_port, exception)
            ServerThread.fail_instance.start()

    def run(self):
        self.server.run()


class Callback(object):
    '''A simple call back object for testing RpcService callbacks'''

    def __init__(self):
        self.called = False
        self.response = None
        self.condition = threading.Condition(threading.Lock())

    def run(self, response):
        self.condition.acquire()
        self.called = True
        self.response = response
        self.condition.notify()
        self.condition.release()


class TestRpcService(unittest.TestCase):
    '''Unit tests for the protobuf.service.RpcService class.'''

    # For testing asynch callback as a method
    callback_method_called = False
    callback_method_request = None
    callback_method_response = None
    callback_method_condition = threading.Condition()

    # Asynch callback method for testing
    @staticmethod
    def callback(request, response):
        TestRpcService.callback_method_condition.acquire()
        TestRpcService.callback_method_called = True
        TestRpcService.callback_method_request = request
        TestRpcService.callback_method_response = response
        TestRpcService.callback_method_condition.notify()
        TestRpcService.callback_method_condition.release()

    def setUp(self):
        self.service = service.RpcService(test_pb2.TestService_Stub,
                                          success_port,
                                          host)
        self.fail_service = service.RpcService(test_pb2.TestService_Stub,
                                          fail_port,
                                          host)
        self.request = test_pb2.Request()
        self.request.str_data = 'I like cheese'

        # Start a server thread
        ServerThread.start_server(
            success_port, fail_port, Exception('YOU FAIL!'))

    def tearDown(self):
        pass

    def test__init__(self):
        '''Test RpcService constructor.'''

        self.assertEqual(self.service.service_stub_class,
                         test_pb2.TestService_Stub,
                         "Attribute 'port' incorrectly initialized")

        self.assertEqual(self.service.port, success_port,
                         "Attribute 'port' incorrectly initialized")

        self.assertEqual(self.service.host, host,
                         "Attribute 'host' incorrectly initialized")

        for method in self.service.service_stub_class.GetDescriptor().methods:
            self.assertNotEqual(self.service.__dict__.get(method.name), None,
                                "method %s not found in service" % method.name)

    def test_call_asynch_callback_object(self):
        '''Test an asynchronous callback object'''
        callback = Callback()
        callback.condition.acquire()
        try:
            self.service.TestMethod(self.request, callback=callback)
        except Exception, e:
            self.assert_(False, 'Caught an unexpected exception %s' % e)

        callback.condition.wait(2.0)
        self.assertEquals(True, callback.called,
                          'Asynch callback was not called')

        # Cannot compare response to None because of bug in protobuf
        # msg compare code
        self.assert_(
            type(callback.response) != None.__class__,
            'Callback response was None')

    def test_call_asynch_callback_method(self):
        '''Test an asynchronous callback method'''
        TestRpcService.callback_method_condition.acquire()
        try:
            self.service.TestMethod(
                self.request, callback=TestRpcService.callback)
        except Exception, e:
            self.fail('Caught an unexpected exception %s' % e)

        TestRpcService.callback_method_condition.wait(2.0)
        self.assertEquals(
            True, TestRpcService.callback_method_called,
            'Asynch callback was not called')

        self.assertEquals(
            self.request, TestRpcService.callback_method_request,
            'Asynch callback request arg not equal to request')

        # Cannot compare reponse to None because of bug in protobuf
        # msg compare code
        self.assert_(
            type(TestRpcService.callback_method_response) != None.__class__,
            'Callback response was None')

    def test_call_synch(self):
        '''Test a synchronous call'''

        try:
            response = self.service.TestMethod(self.request, timeout=1000)
        except Exception, e:
            self.fail('Caught an unexpected exception %s' % e)
        self.assertNotEqual(type(response) != None.__class__,
                            'Callback response was None')

    def test_call_synch_fail(self):
        '''Test a synchronous call'''
        self.assertRaises(
            Exception, self.fail_service.TestMethod, (self.request),
            {'timeout': 1000})
        try:
            reponse = self.fail_service.TestMethod(self.request, timeout=1000)
        except Exception, e:
            self.assertEqual(e.message, "YOU FAIL!")


class TestRpcThread(unittest.TestCase):
    ''' Unit tests for the protobuf.service.RpcThread class.'''

    def setUp(self):
        self.channel = SocketRpcChannel(host=host, port=success_port)
        self.controller = self.channel.newController()
        self.service = test_pb2.TestService_Stub(self.channel)
        self.request = test_pb2.Request()
        self.request.str_data = 'I like cheese'
        self.callback = lambda request, response: response
        self.thread = service.RpcThread(
            test_pb2.TestService_Stub.TestMethod,
            self.service, self.controller, self.request, self.callback)
        ServerThread.start_server(
            success_port, fail_port, Exception('YOU FAIL!'))

    def tearDown(self):
        pass

    def test__init__(self):
        '''Test RpcThread constructor.'''

        self.assertEqual(self.thread.method,
                         test_pb2.TestService_Stub.TestMethod,
                         "Attribute 'method' incorrectly initialized")

        self.assertEqual(self.thread.service,
                         self.service,
                         "Attribute 'service' incorrectly initialized")

        self.assertEqual(self.thread.controller,
                         self.controller,
                         "Attribute 'controller' incorrectly initialized")

        self.assertEqual(self.thread.request,
                         self.request,
                         "Attribute 'request' incorrectly initialized")

        self.assertEqual(self.thread.callback,
                         self.callback,
                         "Attribute 'callback' incorrectly initialized")

        self.assertEqual(self.thread.isDaemon(), True,
                         "Thread not set as Daemon")

    def test_run(self):
        '''Test the run method'''

        try:
            self.thread.run()
        except Exception, e:
            self.assert_(False, "TestRpcThread.run() threw and exception", e)


def suite():
    '''Returns a test suite containing all module tests'''

    suite = unittest.TestSuite()
    suite.addTest(unittest.makeSuite(TestRpcService))
    suite.addTest(unittest.makeSuite(TestRpcThread))
    return suite


if __name__ == '__main__':
    unittest.main()
