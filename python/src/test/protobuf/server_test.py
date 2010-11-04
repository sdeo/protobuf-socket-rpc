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
test_server_protobuf.py - unit tests for the protobuf.server module.

Authors: Eric Saunders (esaunders@lcogt.net)
         Martin Norbury (mnorbury@lcogt.net)
         Jan Dittberner (jan@dittberner.info)

May 2009, Nov 2010
'''

# Modify path to make the script executable.
import sys
sys.path.append('../../main')

# Standard library imports
import unittest

# Third-party imports
from google.protobuf.message import DecodeError

# Module imports
import protobuf.socketrpc.server as server
import protobuf.socketrpc.rpc_pb2 as rpc_pb2
import protobuf.socketrpc.error as error

# Test imports (relative imports)
from fake import FakeSocketFactory, TestServiceImpl
import test_pb2


class TestCallback(unittest.TestCase):
    '''Unit tests for the protobuf.server.Callback class.'''

    def setUp(self):
        self.c = server.Callback()

    def tearDown(self):
        pass

    def test___init__1(self):
        self.assertEqual(self.c.invoked, False,
                         "Attribute 'invoked' incorrectly initialized")

    def test___init__2(self):
        self.assertEqual(self.c.response, None,
                         "Attribute 'response' incorrectly initialized")

    def test_run1(self):
        response = 'ack'
        self.c.run(response)
        self.assertEqual(self.c.response, response,
                         "Attribute 'response' incorrectly updated")

    def test_run2(self):
        response = 'ack'
        self.c.run(response)
        self.assertEqual(self.c.invoked, True,
                         "Attribute 'invoked' incorrectly updated")


class TestSocketHandler(unittest.TestCase):
    '''Unit tests for the protobuf.server.SocketHandler class.'''

    def setUp(self):
        # Define some arbitrary values to satisfy the interface
        self.client_addr = 'here'
        self.server_addr = 'there'

        # Define a simple service request
        self.service_request = test_pb2.Request()
        self.service_request.str_data = 'The lord giveth'
        self.serialized_request = self.service_request.SerializeToString()

        # Define an RPC request with the service request as payload
        self.rpc_request = rpc_pb2.Request()
        self.rpc_request.request_proto = self.serialized_request
        self.testserver = server.SocketRpcServer(8090)

    def tearDown(self):
        pass

    def serializeRpcRequestToSocket(self, partial=False):
        '''Convenience function for preparing socket connection tests.'''

        # Don't validate the RPC request if the partial flag is provided
        if partial:
            bytestream = self.rpc_request.SerializePartialToString()
        else:
            bytestream = self.rpc_request.SerializeToString()

        socket_factory = FakeSocketFactory()
        socket = socket_factory.createSocket()

        return (bytestream, socket)

    def registerTestService(self, exception=None, failmsg=None):
        '''Convenience function to set up a test service.'''

        # Set up a simple test service
        service = TestServiceImpl(exception, failmsg)

        self.testserver.registerService(service)

        return service

    def test_callMethod(self):
        ''' Test normal return for callMethod '''

        # Get the service and method
        service = self.registerTestService()
        method = service.DESCRIPTOR.FindMethodByName("TestMethod")

        # Define an RPC request for an existing service and method
        self.rpc_request.service_name = service.DESCRIPTOR.full_name
        self.rpc_request.method_name = "TestMethod"

         # Serialize the request
        (bytestream, sock) = self.serializeRpcRequestToSocket()

        # Construct the expected response from the service
        expected_response = test_pb2.Response()
        expected_response.str_data = service.response_str_data
        expected_response.int_data = service.response_int_data
        serialized_payload = expected_response.SerializeToString()

        # Wrap the expected response in an RPC Response message
        expected_rpc = rpc_pb2.Response()
        expected_rpc.callback = True
        expected_rpc.response_proto = serialized_payload

        handler = server.SocketHandler(sock, self.client_addr,
                                       self.server_addr,
                                       self.testserver)

        response = handler.callMethod(service, method, self.service_request)

        self.assertEquals(response, expected_rpc, 'Normal response')

    def test_callMethod_RPC_FAILED(self):
        '''Test for RPCFAILED state.'''

        # Get the service and method
        failmsg = "User defined error"
        service = self.registerTestService(None, failmsg)
        method = service.DESCRIPTOR.FindMethodByName("TestMethod")

        # Define an RPC request for an existing service and method
        self.rpc_request.service_name = service.DESCRIPTOR.full_name
        self.rpc_request.method_name = "TestMethod"

         # Serialize the request
        (bytestream, sock) = self.serializeRpcRequestToSocket()

        handler = server.SocketHandler(sock, self.client_addr,
                                       self.server_addr,
                                       self.testserver)

        response = handler.callMethod(service, method, self.service_request)

        self.assertEquals(response.error_reason, rpc_pb2.RPC_FAILED,
                          'RPC_FAILED - error code')
        self.assertEquals(response.error, failmsg, 'RPC_FAILED - messsage')

    def test_callMethod_RPC_ERROR(self):
        '''Test for RPCERROR state.'''

        # Get the service and method
        exception = Exception('An exception has been raised')
        service = self.registerTestService(exception)
        method = service.DESCRIPTOR.FindMethodByName("TestMethod")

        # Define an RPC request for an existing service and method
        self.rpc_request.service_name = service.DESCRIPTOR.full_name
        self.rpc_request.method_name = "TestMethod"

         # Serialize the request
        (bytestream, sock) = self.serializeRpcRequestToSocket()

        handler = server.SocketHandler(sock, self.client_addr,
                                       self.server_addr,
                                       self.testserver)

        self.assertRaises(
            error.RpcError, handler.callMethod, service, method,
            self.service_request)

    def test_parseServiceRequest(self):
        '''Test normal return from parseServiceRequest.'''

        # Define the required fields of the RPC request
        self.rpc_request.service_name = 'service_full_name'
        self.rpc_request.method_name = 'method_name'

        # Serialize the request
        (bytestream, sock) = self.serializeRpcRequestToSocket()

        handler = server.SocketHandler(sock, self.client_addr,
                                       self.server_addr, self.testserver)
        self.assertEqual(handler.parseServiceRequest(bytestream),
                         self.rpc_request, "Parsing request - normal return")

    def test_parseServiceRequest_BAD_REQUEST_DATA(self):
        '''
        Test the correct error is raised after sending an invalid
        request.
        '''
        # Define an invalid RPC request (missing required service name)
        self.rpc_request.method_name = 'method_name'

        # Serialize the request (without checking initialisation status)
        (bytestream, sock) = self.serializeRpcRequestToSocket(partial=True)

        # Test the server handler raises the BAD_REQUEST_DATA error code
        handler = server.SocketHandler(sock, self.client_addr,
                                       self.server_addr, self.testserver)
        self.assertRaises(error.BadRequestDataError,
                          handler.parseServiceRequest, bytestream)

    def test_parseServiceRequest_junk_input(self):
        '''Test the correct error is raised after sending complete crap.'''

        # Bind an arbitrary bytestream to the socket
        bytestream = 'ABCD'
        socket_factory = FakeSocketFactory()
        sock = socket_factory.createSocket()

        # Test the server handler raises the BAD_REQUEST_DATA error code
        handler = server.SocketHandler(sock, self.client_addr,
                                       self.server_addr, self.testserver)
        self.assertRaises(error.BadRequestDataError,
                          handler.parseServiceRequest, bytestream)

    def test_retrieveService(self):
        '''Test normal return from retrieveService.'''

        # Add a test service
        expected_service = self.registerTestService()

        # Define an RPC request for an existing service and method
        self.rpc_request.service_name = expected_service.DESCRIPTOR.full_name
        self.rpc_request.method_name = "TestMethod"

        # Serialize the request and create the socket handler
        (bytestream, sock) = self.serializeRpcRequestToSocket()
        handler = server.SocketHandler(sock, self.client_addr,
                                       self.server_addr, self.testserver)

        # Run the method on the server
        received_service = handler.retrieveService(
            self.rpc_request.service_name)
        self.assertEqual(received_service, expected_service, 'Service found')

    def test_retrieveService_SERVICE_NOT_FOUND(self):
        '''Test exceptional return from retrieveService.'''

        # Add a test service
        expected_service = self.registerTestService()

        # Define an RPC request for a non-existent service and method
        self.rpc_request.service_name = "Non-existent service"
        self.rpc_request.method_name = "Dummy method"

        # Serialize the request and create the socket handler
        (bytestream, sock) = self.serializeRpcRequestToSocket()
        handler = server.SocketHandler(sock, self.client_addr,
                                       self.server_addr, self.testserver)

        # Run the method on the server
        self.assertRaises(error.ServiceNotFoundError, handler.retrieveService,
                          self.rpc_request.service_name)

    def test_retrieveMethod(self):
        '''Test normal return from retrieveMethod.'''

        # Add a test service
        expected_service = self.registerTestService()
        expected_method = expected_service.DESCRIPTOR.FindMethodByName(
            "TestMethod")

        # Define an RPC request for an existing service and method
        self.rpc_request.service_name = expected_service.DESCRIPTOR.full_name
        self.rpc_request.method_name = "TestMethod"

        # Serialize the request and create the socket handler
        (bytestream, sock) = self.serializeRpcRequestToSocket()
        handler = server.SocketHandler(sock, self.client_addr,
                                       self.server_addr, self.testserver)

        # Run the method on the server
        received_method = handler.retrieveMethod(expected_service,
                                                 self.rpc_request.method_name)
        self.assertEqual(received_method, expected_method, 'Method found')

    def test_retrieveMethod_METHOD_NOT_FOUND(self):
        '''Test exceptional return from retrieveMethod.'''

        # Add a test service
        expected_service = self.registerTestService()
        expected_method = expected_service.DESCRIPTOR.FindMethodByName(
            "TestMethod")

        # Define an RPC request for an existing service and method
        self.rpc_request.service_name = expected_service.DESCRIPTOR.full_name
        self.rpc_request.method_name = "Non-existent method"

        # Serialize the request and create the socket handler
        (bytestream, sock) = self.serializeRpcRequestToSocket()
        handler = server.SocketHandler(sock, self.client_addr,
                                       self.server_addr, self.testserver)

        # Run the method on the server
        self.assertRaises(error.MethodNotFoundError, handler.retrieveMethod,
                          expected_service, self.rpc_request.method_name)

    def test_retrieveProtoRequest(self):
        '''Test normal return from retrieveMethod.'''

        # Protocol message
        expected_str_data = self.service_request.str_data

        # Add a test service
        expected_service = self.registerTestService()
        expected_method = expected_service.DESCRIPTOR.FindMethodByName(
            "TestMethod")

        # Define an RPC request for an existing service and method
        self.rpc_request.service_name = expected_service.DESCRIPTOR.full_name
        self.rpc_request.method_name = "TestMethod"

         # Serialize the request and create the socket handler
        (bytestream, sock) = self.serializeRpcRequestToSocket()
        handler = server.SocketHandler(sock, self.client_addr,
                                       self.server_addr, self.testserver)

        proto_request = handler.retrieveProtoRequest(
            expected_service, expected_method, self.rpc_request)

        self.assertEqual(proto_request.str_data, expected_str_data,
                         'Normal response')

    def test_retrieveProtoRequest_BAD_REQUEST_PROTO(self):
        '''Test exceptional return from retrieveProtoRequest.'''

         # Protocol message
        expected_str_data = self.service_request.str_data

        # Add a test service
        expected_service = self.registerTestService()
        expected_method = expected_service.DESCRIPTOR.FindMethodByName(
            "TestMethod")

        # Define an RPC request for an existing service and method
        self.rpc_request.service_name = expected_service.DESCRIPTOR.full_name
        self.rpc_request.method_name = "TestMethod"

         # Serialize the request and create the socket handler
        (bytestream, sock) = self.serializeRpcRequestToSocket()
        handler = server.SocketHandler(sock, self.client_addr,
                                       self.server_addr, self.testserver)

        # Force a bad protocol message
        self.rpc_request.request_proto = "Bad protocol message"

        # Run the method
        self.assertRaises(error.BadRequestProtoError,
                          handler.retrieveProtoRequest,
                          expected_service, expected_method, self.rpc_request)

    def test_validateAndExecuteRequest(self):
        '''Test a request for an existing service and method.'''

        # Add a test service
        service = self.registerTestService()

        # Define an RPC request for an existing service and method
        self.rpc_request.service_name = service.DESCRIPTOR.full_name
        self.rpc_request.method_name = "TestMethod"

        # Serialize the request
        (bytestream, sock) = self.serializeRpcRequestToSocket()

        # Construct the expected response from the service
        expected_response = test_pb2.Response()
        expected_response.str_data = service.response_str_data
        expected_response.int_data = service.response_int_data
        serialized_payload = expected_response.SerializeToString()

        expected_rpc = rpc_pb2.Response()
        expected_rpc.callback = True
        expected_rpc.response_proto = serialized_payload

        # Run the method on the server
        handler = server.SocketHandler(sock, self.client_addr,
                                       self.server_addr, self.testserver)
        received_rpc = handler.validateAndExecuteRequest(bytestream)

        # Check the response message error code
        self.assertEqual(received_rpc, expected_rpc, 'Normal response')

    def test_validateAndExecuteRequest_SERVICE_NOT_FOUND(self):
        '''Test a request for a non-existent service.'''

        # Define an RPC request for a non-existent service
        self.rpc_request.service_name = "Non-existent service"
        self.rpc_request.method_name = "Dummy method"

        # Serialize the request
        (bytestream, sock) = self.serializeRpcRequestToSocket()

        # Run the method on the server
        handler = server.SocketHandler(sock, self.client_addr,
                                       self.server_addr, self.testserver)
        response = handler.validateAndExecuteRequest(bytestream)

        # Check the response message error code
        self.assertEqual(response.error_reason, rpc_pb2.SERVICE_NOT_FOUND,
                         'Unexpected error code')

    def test_validateAndExecuteRequest_METHOD_NOT_FOUND(self):
        '''Test a request for a non-existent method.'''

        # Add a test service
        service = self.registerTestService()

        # Define an RPC request for an existing service but non-existent method
        self.rpc_request.service_name = service.DESCRIPTOR.full_name
        self.rpc_request.method_name = 'Dummy method'

        # Serialize the request
        (bytestream, sock) = self.serializeRpcRequestToSocket()

        # Run the method on the server
        handler = server.SocketHandler(sock, self.client_addr,
                                       self.server_addr, self.testserver)
        response = handler.validateAndExecuteRequest(bytestream)

        # Check the response message error code
        self.assertEqual(response.error_reason, rpc_pb2.METHOD_NOT_FOUND,
                         'Unexpected error code')


class TestSocketRpcServer(unittest.TestCase):
    '''Unit tests for the protobuf.server.SocketRpcServer class.'''

    def setUp(self):
        self.port = 8090
        self.server = server.SocketRpcServer(self.port)

    def tearDown(self):
        pass

    def test___init__(self):
        self.assertEqual(self.server.port, self.port,
                         "Attribute 'port' incorrectly initialized")

    def test_register_service(self):
        self.service = TestServiceImpl()
        self.server.registerService(self.service)

        self.map = {'protobuf.socketrpc.TestService': self.service}

        self.assertEqual(self.server.serviceMap, self.map,
                         "Adding service to internal map")


def suite():
    '''Return the test suite containing all tests from this module.'''

    suite = unittest.TestSuite()
    suite.addTest(unittest.makeSuite(TestCallback))
    suite.addTest(unittest.makeSuite(TestSocketHandler))
    suite.addTest(unittest.makeSuite(TestSocketRpcServer))
    return suite


if __name__ == '__main__':
    unittest.main()
