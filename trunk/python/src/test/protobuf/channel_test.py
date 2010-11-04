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
channel_test.py - unit tests for the protobuf.channel module

Authors: Eric Saunders (esaunders@lcogt.net)
         Martin Norbury (mnorbury@lcogt.net)
         Jan Dittberner (jan@dittberner.info)

May 2009, Nov 2010
'''

import unittest

# Add protobuf module to the classpath
import sys
sys.path.append('../../main')

# Import the class to test
import protobuf.socketrpc.channel as ch
import protobuf.socketrpc.error as error

# Import the fake stub classes used in testing to simulate sockets etc.
from fake import FakeSocketFactory, FakeSocket, FakeCallback, TestServiceImpl

# Import the RPC definition class and the test service class
import protobuf.socketrpc.rpc_pb2 as rpc_pb2
import test_pb2


class TestSocketRpcChannel(unittest.TestCase):
    '''Unit tests for the protobuf.channel.SocketRpcChannel class.'''

    def setUp(self):

        # Create a channel connected to a fake socket
        self.factory = FakeSocketFactory()
        self.channel = ch.SocketRpcChannel(socketFactory=self.factory)

        # Define a simple service request
        self.service_request = test_pb2.Request()
        self.service_request.str_data = 'The lord giveth'
        self.serialized_request = self.service_request.SerializeToString()

        # Define a service response
        self.service_response = test_pb2.Response()
        self.service_response.str_data = 'And the lord taketh away'
        self.serialized_response = \
            self.service_response.SerializePartialToString()

        # Define an RPC request with the service request as payload
        self.rpc_request = rpc_pb2.Request()
        self.rpc_request.request_proto = self.serialized_request

    def tearDown(self):
        pass

    def test___init__1(self):
        self.assertEqual(self.channel.sockFactory, self.factory,
                         'Initialising channel with user-supplied factory')

    def test___init__defaults(self):
        self.assert_(self.channel.host, True)
        self.assert_(self.channel.port, True)

    def test_validateRequest(self):
        self.rpc_request.service_name = "Dummy Service"
        self.rpc_request.method_name = "Dummy Method"

        self.assertEqual(self.channel.validateRequest(self.rpc_request), None,
                    'validateRequest - valid request provided')

    def test_validateRequest_BAD_REQUEST_PROTO(self):

        # A request with mandatory fields missing
        self.rpc_request = rpc_pb2.Request()

        self.assertRaises(error.BadRequestProtoError,
                          self.channel.validateRequest,
                          self.rpc_request)

    def test_openSocket(self):
        '''Test normal return from openSocket.'''
        self.assert_(self.channel.openSocket, "openSocket returns something")

    def test_openSocket_IO_ERROR(self):
        '''Test exceptional return from openSocket (IO_ERROR).'''

        # Fake socket primed to throw an unknown host exception
        socket = FakeSocket()
        socket.throwIOErrorException()
        self.factory.setSocket(socket)

        self.assertRaises(error.IOError, self.channel.openSocket,
                          'host', -1)

    def test_openSocket_UNKNOWN_HOST(self):
        '''Test exceptional return from openSocket (UNKNOWN_HOST).'''
        self.assert_(self.channel.openSocket, "openSocket returns something")

        # Fake socket primed to throw an unknown host exception
        socket = FakeSocket()
        socket.throwUnknownHostException()
        self.factory.setSocket(socket)

        self.assertRaises(error.UnknownHostError, self.channel.openSocket,
                          'host', -1)

    def test_createRpcRequest(self):
        '''Test createRpcRequest - normal usage.'''

        # Instantiate the test service, and get a reference to the method
        method_name = 'TestMethod'
        service = TestServiceImpl()
        method = service.DESCRIPTOR.FindMethodByName(method_name)

        # Define a simple service request
        service_request = test_pb2.Request()
        service_request.str_data = 'The lord giveth'
        serialized_request = service_request.SerializeToString()

        # Define an RPC request with the service request as payload
        expected_rpc = rpc_pb2.Request()
        expected_rpc.request_proto = serialized_request
        expected_rpc.service_name = service.DESCRIPTOR.full_name
        expected_rpc.method_name = method_name

        self.assertEqual(
            self.channel.createRpcRequest(method, service_request),
            expected_rpc, 'createRpcRequest - normal usage')

    def test_sendRpcMessage(self):
        '''Test sendRpcMessage - normal usage.'''

        # Create a socket and service request
        sock = self.factory.createSocket()
        sent_request = self.rpc_request
        sent_request.service_name = "Dummy service"
        sent_request.method_name = "Dummy method"

        # Call the method
        self.channel.sendRpcMessage(sock, sent_request)

        # Extract the output that was written to the socket
        received_request = rpc_pb2.Request()
        received_request.MergeFromString(sock.output_stream.stream_data)

        self.assertEqual(received_request, sent_request,
                         'Request written to socket')

    def test_sendRpcMessage_IOError(self):
        '''Test sendRpcMessage - IOError.'''

        # Create a socket with an IOError condition set
        sock = self.factory.createSocket()
        sock.throwIOErrorException()

        # Create a service request
        sent_request = self.rpc_request
        sent_request.service_name = "Dummy service"
        sent_request.method_name = "Dummy method"

        self.assertRaises(error.IOError, self.channel.sendRpcMessage, sock,
                          sent_request)

    def test_recvRpcMessage(self):
        '''Test recvRpcMessage - normal usage.'''

        # Create a socket and service request
        msg = 'Message from server'
        sock = self.factory.createSocket()
        sock.withInputBytes(msg)

        # Call the method
        self.assertEqual(self.channel.recvRpcMessage(sock), msg,
                         'recvRpcMessage - normal usage')

    def test_recvRpcMessage_ioerror(self):
        '''Test recvRpcMessage - IOError.'''

        # Create a socket and service request
        msg = 'Message from server'
        sock = self.factory.createSocket()
        sock.withInputBytes(msg)
        sock.throwIOErrorException()

        # Call the method
        self.assertRaises(error.IOError, self.channel.recvRpcMessage, sock)

    def test_parseResponse(self):
        '''Test parseResponse - normal usage.'''
        resp_class = rpc_pb2.Response
        expected_response = resp_class()
        bytestream = expected_response.SerializeToString()

        self.assertEqual(self.channel.parseResponse(bytestream, resp_class),
                         expected_response, 'parseResponse - normal usage')

    def test_parseResponse_junk_input(self):
        '''Test the correct error is raised after sending complete crap.'''

        # Setup an arbitrary and broken bytestream
        bytestream = 'ABCD'
        resp_class = rpc_pb2.Response

        self.assertRaises(error.BadResponseProtoError,
                          self.channel.parseResponse, bytestream, resp_class)

    def testGoodRpc(self):
        '''Test a good RPC call.'''

        # Fake socket with prepared response
        socket = FakeSocket()
        socket.withResponseProto(self.service_response)
        socketFactory = FakeSocketFactory()
        socketFactory.setSocket(socket)

        # Create channel
        channel = ch.SocketRpcChannel("host", -1, socketFactory)
        controller = channel.newController()

        # Create the service
        service = test_pb2.TestService_Stub(channel)

        # Call RPC method
        callback = FakeCallback()
        service.TestMethod(controller, self.service_request, callback)

        self.assertTrue(callback.invoked, 'Callback invoked')
        self.assertEquals(self.service_response.str_data,
                          callback.response.str_data, 'Response message')
        self.assertEquals(self.serialized_request,
                          socket.getRequest().request_proto,
                          'Request protocol serialisation')
        self.assertEquals(service.DESCRIPTOR.full_name,
                          socket.getRequest().service_name, 'Service name')
        self.assertEquals(service.DESCRIPTOR.methods[0].name,
                          socket.getRequest().method_name, 'Method name')

    def testUnknownHostException(self):
        '''Test unknown host.'''

        # Fake socket primed to throw an unknown host exception
        socket = FakeSocket()
        socket.throwUnknownHostException()
        socketFactory = FakeSocketFactory()
        socketFactory.setSocket(socket)

        # Create channel
        channel = ch.SocketRpcChannel("host", -1, socketFactory)
        controller = channel.newController()

        # Create the service
        service = test_pb2.TestService_Stub(channel)

        # Call RPC method
        callback = FakeCallback()
        service.TestMethod(controller, self.service_request, callback)

        self.assertFalse(callback.invoked, 'Callback invoked')
        self.assertTrue(controller.failed())
        self.assertEquals(rpc_pb2.UNKNOWN_HOST, controller.reason,
                          'Error reason')

    def testIOErrorWhileCreatingSocket(self):
        '''Test Error while creating socket.'''

        # Fake socket primed to throw an unknown host exception
        socket = FakeSocket()
        socket.throwIOErrorException()
        socketFactory = FakeSocketFactory()
        socketFactory.setSocket(socket)

         # Create channel
        channel = ch.SocketRpcChannel("host", -1, socketFactory)
        controller = channel.newController()

        # Create the service
        service = test_pb2.TestService_Stub(channel)

        # Call RPC method
        callback = FakeCallback()
        service.TestMethod(controller, self.service_request, callback)

        self.assertFalse(callback.invoked, 'Callback invoked')
        self.assertTrue(controller.failed())
        self.assertEquals(rpc_pb2.IO_ERROR, controller.reason, 'Error reason')

    def testIncompleteRequest(self):
        '''Test calling RPC with incomplete request.'''

        # Create data
        service_request = test_pb2.Request()

        # Fake socket with prepared response
        socket = FakeSocket()
        socket.withResponseProto(self.service_response)
        socketFactory = FakeSocketFactory()
        socketFactory.setSocket(socket)

        # Create channel
        channel = ch.SocketRpcChannel("host", -1, socketFactory)
        controller = channel.newController()

        # Create the service
        service = test_pb2.TestService_Stub(channel)

        # Call RPC method
        callback = FakeCallback()
        service.TestMethod(controller, service_request, callback)

        self.assertFalse(callback.invoked, 'Callback invoked')
        self.assertEquals(rpc_pb2.BAD_REQUEST_PROTO, controller.reason)
        self.assertTrue(controller.failed())

    def testNoCallBack(self):
        '''Test RPC failing to invoke callback.'''

        # Fake socket with callback set to false
        socket = FakeSocket()
        socket.withNoResponse(False)
        socketFactory = FakeSocketFactory()
        socketFactory.setSocket(socket)

        # Create channel
        channel = ch.SocketRpcChannel("host", -1, socketFactory)
        controller = channel.newController()

        # Create the service
        service = test_pb2.TestService_Stub(channel)

        # Call RPC method
        callback = FakeCallback()
        service.TestMethod(controller, self.service_request, callback)

        self.assertFalse(callback.invoked, 'Callback invoked')
        self.assertEquals(self.serialized_request,
                          socket.getRequest().request_proto,
                          'Request protocol serialisation')
        self.assertEquals(service.DESCRIPTOR.full_name,
                          socket.getRequest().service_name, 'Service name')
        self.assertEquals(service.DESCRIPTOR.methods[0].name,
                          socket.getRequest().method_name, 'Method name')

    def testBadResponse(self):
        '''Test bad response from server.'''

        # Fake socket with prepared response
        socket = FakeSocket()
        socket.withInputBytes("bad response")
        socketFactory = FakeSocketFactory()
        socketFactory.setSocket(socket)

        # Create channel
        channel = ch.SocketRpcChannel("host", -1, socketFactory)
        controller = channel.newController()

        # Create the service
        service = test_pb2.TestService_Stub(channel)

        # Call RPC method
        callback = FakeCallback()
        service.TestMethod(controller, self.service_request, callback)

        # Verify request was sent and bad response received
        self.assertFalse(callback.invoked, 'Callback invoked')
        self.assertEquals(self.serialized_request,
                          socket.getRequest().request_proto,
                          'Request protocol serialisation')
        self.assertTrue(controller.failed(), 'Controller failed')
        self.assertEquals(rpc_pb2.BAD_RESPONSE_PROTO, controller.reason,
                          'Controller reason')


class Test__LifeCycle(unittest.TestCase):
    '''Unit tests for the protobuf.channel._Lifecycle class.'''

    def setUp(self):
        # Create a channel connected to a fake socket
        self.factory = FakeSocketFactory()
        self.socket = FakeSocket()
        self.channel = ch.SocketRpcChannel(socketFactory=self.factory)
        self.controller = self.channel.newController()

        self.lc = ch._LifeCycle(self.controller, self.channel)

        self.factory.setSocket(self.socket)

        # Define a simple service request
        self.service_request = test_pb2.Request()
        self.service_request.str_data = 'The lord giveth'
        self.serialized_request = self.service_request.SerializeToString()

        # Define an RPC request with the service request as payload
        self.rpc_request = rpc_pb2.Request()
        self.rpc_request.request_proto = self.serialized_request

    def tearDown(self):
        pass

    def test___init__(self):
        '''Test _LifeCycle constructor.'''

        self.assertEqual(self.lc.controller, self.controller,
                         "Attribute 'controller' incorrectly initialized")

        self.assertEqual(self.lc.channel, self.channel,
                         "Attribute 'channel' incorrectly initialized")

        self.assertEqual(self.lc.sock, None,
                         "Attribute 'sock' incorrectly initialized")

        self.assertEqual(self.lc.byte_stream, None,
                         "Attribute 'byte_stream' incorrectly initialized")

        self.assertEqual(self.lc.rpcResponse, None,
                         "Attribute 'rpcResponse' incorrectly initialized")

        self.assertEqual(self.lc.serviceResponse, None,
                         "Attribute 'serviceResponse' incorrectly initialized")

    def test_tryToValidateRequest(self):
        '''Test tryToValidateRequest - normal usage.'''

        self.assertEquals(self.lc.tryToValidateRequest(self.rpc_request),
                     None, "tryToValidateRequest - valid request")

    def test_tryToValidateRequest_con_error(self):
        '''Test tryToValidateRequest - controller in error state.'''

        self.assertEquals(self.lc.tryToValidateRequest(self.rpc_request),
                     None, "tryToValidateRequest - controller in error state")

    def test_tryToValidateRequest_BAD_REQUEST_PROTO(self):
        '''Test tryToValidateRequest - BadRequestProto error thrown.'''

        # A request with mandatory fields missing
        self.rpc_request = rpc_pb2.Request()

        self.lc.tryToValidateRequest(self.rpc_request)

        self.assertEquals(self.controller.reason, rpc_pb2.BAD_REQUEST_PROTO,
                         "tryToValidateRequest - invalid request")

        self.assertEquals(self.controller.failed(), True,
                          "tryToValidateRequest - invalid request")

    def test_tryToOpenSocket(self):
        '''Test tryToOpenSocket - normal usage.'''

        self.lc.tryToOpenSocket()
        self.assert_(self.lc.sock)

    def test_tryToOpenSocket_con_error(self):
        '''Test tryToOpenSocket - controller in error state.'''

        self.controller._fail = True
        self.lc.tryToOpenSocket()
        self.assertEquals(self.lc.sock, None,
                          "tryToOpenSocket - controller in error state")

    def test_tryToOpenSocket_UNKNOWN_HOST(self):
        '''Test tryToOpenSocket - UnknownHost error thrown.'''

        self.socket.throwUnknownHostException()

        self.lc.tryToOpenSocket()
        self.assertEquals(self.lc.sock, None,
                          "tryToOpenSocket - UNKNOWN_HOST error")

        self.assertEquals(self.controller.reason, rpc_pb2.UNKNOWN_HOST,
                          "tryToOpenSocket - UNKNOWN_HOST error")

        self.assertEquals(self.controller.failed(), True,
                          "tryToOpenSocket - UNKNOWN_HOST error")

    def test_tryToOpenSocket_IO_ERROR(self):
        '''Test tryToOpenSocket - IOError error thrown.'''

        self.socket.throwIOErrorException()

        self.lc.tryToOpenSocket()
        self.assertEquals(self.lc.sock, None,
                          "tryToOpenSocket - IO_ERROR error")

        self.assertEquals(self.controller.reason, rpc_pb2.IO_ERROR,
                          "tryToOpenSocket - IO_ERROR error")

        self.assertEquals(self.controller.failed(), True,
                          "tryToOpenSocket - IO_ERROR error")

    def test_tryToSendRpcRequest(self):
        '''Test tryToSendRpcRequest - normal usage.'''

        # Instantiate the test service, and get a reference to the method
        method_name = 'TestMethod'
        service = TestServiceImpl()
        method = service.DESCRIPTOR.FindMethodByName(method_name)

        # Set the service and method names of the RPC request
        self.rpc_request.service_name = service.DESCRIPTOR.full_name
        self.rpc_request.method_name = method_name

        # Add the socket instance to the lifecycle object
        self.lc.sock = self.socket

        self.assertEquals(
            self.lc.tryToSendRpcRequest(method, self.rpc_request),
            None, "tryToSendRpcRequest - normal return")

    def test_tryToSendRpcRequest_IO_ERROR(self):
        '''Test tryToSendRpcRequest - IOError error thrown.'''

        # Instantiate the test service, and get a reference to the method
        method_name = 'TestMethod'
        service = TestServiceImpl()
        method = service.DESCRIPTOR.FindMethodByName(method_name)

        # Set the service and method names of the RPC request
        self.rpc_request.service_name = service.DESCRIPTOR.full_name
        self.rpc_request.method_name = method_name

        # Set the exception, and add the socket instance to the
        # lifecycle object
        self.socket.throwIOErrorException()
        self.lc.sock = self.socket

        self.assertEquals(
            self.lc.tryToSendRpcRequest(method, self.rpc_request),
            None, "tryToSendRpcRequest - IO_ERROR")

        self.assertEquals(self.controller.reason, rpc_pb2.IO_ERROR,
                          "tryToSendRpcRequest - IO_ERROR error")

        self.assertEquals(self.controller.failed(), True,
                          "tryToSendRpcRequest - IO_ERROR error")

    def test_tryToReceiveReply(self):
        '''Test tryToReceiveReply - normal usage.'''

        # Add some data to the socket
        msg = 'Message from server'
        self.socket.withInputBytes(msg)
        self.lc.sock = self.socket

        self.assertEquals(self.lc.tryToReceiveReply(), None,
                          "tryToReceiveReply - normal usage")

        # Verify the socket has been closed
        self.assert_(self.socket.input_stream.closed,
                     "tryToReceiveReply - normal usage")

    def test_tryToReceiveReply_IOError(self):
        '''Test tryToReceiveReply - IOError thrown.'''

        # Add some data to the socket
        msg = 'Message from server'
        self.socket.withInputBytes(msg)
        self.socket.throwIOErrorException()
        self.lc.sock = self.socket

        self.assertEquals(self.lc.tryToReceiveReply(), None,
                          "tryToReceiveReply - IO_ERROR error")

        self.assertEquals(self.controller.reason, rpc_pb2.IO_ERROR,
                          "tryToReceiveReply - IO_ERROR error")

        self.assertEquals(self.controller.failed(), True,
                          "tryToReceiveReply - IO_ERROR error")

        # Verify the socket has been closed
        self.assert_(self.socket.input_stream.closed,
                     "tryToReceiveReply - IO_ERROR error")

    def test_tryToParseReply(self):
        '''Test tryToParseReply - normal usage.'''

        resp_class = rpc_pb2.Response
        expected_response = resp_class()
        self.lc.byte_stream = expected_response.SerializeToString()

        self.assertEquals(self.lc.tryToParseReply(), None,
                          "tryToParseReply - normal usage")

    def test_tryToParseReply_BAD_RESPONSE_PROTO(self):
        '''Test tryToParseReply - BadResponseProto error thrown.'''

        # Setup an arbitrary and broken bytestream
        self.lc.byte_stream = 'ABCD'

        self.assertEquals(self.lc.tryToParseReply(), None,
                          "tryToParseReply - BAD_RESPONSE_PROTO error")

        self.assertEquals(self.controller.reason, rpc_pb2.BAD_RESPONSE_PROTO,
                       "tryToParseReply - BAD_RESPONSE_PROTO error")

        self.assertEquals(self.controller.failed(), True,
                          "tryToParseReply - BAD_RESPONSE_PROTO error")

    def test_tryToRetrieveServiceResponse(self):
        '''Test tryToRetrieveServiceResponse - normal usage.'''

        resp_class = rpc_pb2.Response
        expected_response = resp_class()

        self.lc.byte_stream = expected_response.SerializeToString()
        self.lc.rpcResponse = expected_response

        self.assertEquals(self.lc.tryToRetrieveServiceResponse(resp_class),
                          None, "tryToRetrieveServiceResponse - normal usage")

    def test_tryToRetrieveServiceResponse_BAD_RESPONSE_PROTO(self):
        '''tryToRetrieveServiceResponse - BadResponseProto

            This error can never trigger, since all fields of an RPC
            Response() object are optional!'''

        pass

    def test_tryToRunCallback(self):
        '''Test tryToRunCallback - normal usage.'''

        callback = FakeCallback()
        self.lc.rpcResponse = rpc_pb2.Response()

        self.assertEquals(self.lc.tryToRunCallback(callback), None,
                          "tryToRunCallback - normal usage")


def suite():
    '''Return the test suite containing all tests from this module.'''
    suite = unittest.TestSuite()
    suite.addTest(unittest.makeSuite(TestSocketRpcChannel))
    suite.addTest(unittest.makeSuite(Test__LifeCycle))

    return suite


if __name__ == '__main__':
    unittest.main()
