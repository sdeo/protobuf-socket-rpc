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
channel.py - Socket implementation of Google's Protocol Buffers RPC
service interface.

This package contains classes providing a socket implementation of the
RPCChannel abstract class.

Authors: Martin Norbury (mnorbury@lcogt.net)
         Eric Saunders (esaunders@lcogt.net)
         Jan Dittberner (jan@dittberner.info)

May 2009, Nov 2010
'''

# Standard library imports
import socket

# Third party imports
import google.protobuf.service as service

# Module imports
import rpc_pb2 as rpc_pb
from protobuf.socketrpc import logger
from protobuf.socketrpc.controller import SocketRpcController
from protobuf.socketrpc import error

# Configure package logging
log = logger.getLogger(__name__)


class SocketFactory():
    '''A factory class for providing socket instances.'''

    def createSocket(self):
        '''Creates and returns a TCP socket.'''
        return socket.socket(socket.AF_INET, socket.SOCK_STREAM)


class SocketRpcChannel(service.RpcChannel):
    '''Socket implementation of an RpcChannel.

    An RpcChannel represents a communication line to a service which
    can be used to call the service's methods.

    Example:
       channel    = SocketRpcChannel(host='myservicehost')
       controller = channel.newController()
       service    = MyService_Stub(channel)
       service.MyMethod(controller,request,callback)
    '''

    def __init__(self, host='localhost', port=8090,
                 socketFactory=SocketFactory()):
        '''SocketRpcChannel to connect to a socket server
        on a user defined port.
        '''
        self.host = host
        self.port = port
        self.sockFactory = socketFactory

    def newController(self):
        '''Create and return a socket controller.'''
        return SocketRpcController()

    def validateRequest(self, request):
        '''Validate the client request against the protocol file.'''

        # Check the request is correctly initialized
        if not request.IsInitialized():
            raise error.BadRequestProtoError('Client request is missing\
                                              mandatory fields')

    def openSocket(self, host, port):
        '''Open a socket connection to a given host and port.'''

        # Open socket
        sock = self.sockFactory.createSocket()

        # Connect socket to server - defined by host and port arguments
        try:
            sock.connect((host, port))
        except socket.gaierror:
            msg = "Could not find host %s" % host

            # Cleanup and re-raise the exception with the caller
            self.closeSocket(sock)
            raise error.UnknownHostError(msg)
        except socket.error:
            msg = "Could not open I/O for %s:%s" % (host, port)

            # Cleanup and re-raise the exception with the caller
            self.closeSocket(sock)
            raise error.IOError(msg)

        return sock

    def createRpcRequest(self, method, request):
        '''Wrap the user's request in an RPC protocol message.'''
        rpcRequest = rpc_pb.Request()
        rpcRequest.request_proto = request.SerializeToString()
        rpcRequest.service_name = method.containing_service.full_name
        rpcRequest.method_name = method.name

        return rpcRequest

    def sendRpcMessage(self, sock, rpcRequest):
        '''Send an RPC request to the server.'''
        try:
            wfile = sock.makefile('w')
            wfile.write(rpcRequest.SerializeToString())
            wfile.flush()
            sock.shutdown(socket.SHUT_WR)
        except socket.error:
            self.closeSocket(sock)
            raise error.IOError("Error writing data to server")

    def recvRpcMessage(self, sock):
        '''Handle reading an RPC reply from the server.'''
        try:
            rfile = sock.makefile('r')
            byte_stream = rfile.read()
        except socket.error:
            raise error.IOError("Error reading data from server")
        finally:
            self.closeSocket(sock)

        return byte_stream

    def parseResponse(self, byte_stream, response_class):
        '''Parse a bytestream into a Response object of the requested type.'''

        # Instantiate a Response object of the requested type
        response = response_class()

        # Catch anything which isn't a valid PB bytestream
        try:
            response.ParseFromString(byte_stream)
        except Exception, e:
            raise error.BadResponseProtoError("Invalid response \
                                              (decodeError): " + str(e))

        # Check the response has all mandatory fields initialized
        if not response.IsInitialized():
            raise error.BadResponseProtoError("Response not initialized")

        return response

    def closeSocket(self, sock):
        '''Close the socket.'''
        if sock:
            try:
                sock.close()
            except:
                pass
        return

    def CallMethod(self, method, controller, request, response_class, done):
        '''Call the RPC method.

        This method uses a LifeCycle instance to manage communication
        with the server.
        '''
        lifecycle = _LifeCycle(controller, self)
        lifecycle.tryToValidateRequest(request)
        lifecycle.tryToOpenSocket()
        lifecycle.tryToSendRpcRequest(method, request)
        lifecycle.tryToReceiveReply()
        lifecycle.tryToParseReply()
        lifecycle.tryToRetrieveServiceResponse(response_class)
        lifecycle.tryToRunCallback(done)


class _LifeCycle():
    '''Represents and manages the lifecycle of an RPC request.'''

    def __init__(self, controller, channel):
        self.controller = controller
        self.channel = channel
        self.sock = None
        self.byte_stream = None
        self.rpcResponse = None
        self.serviceResponse = None

    def tryToValidateRequest(self, request):
        if self.controller.failed():
            return

        # Validate the request object
        try:
            self.channel.validateRequest(request)
        except error.BadRequestProtoError, e:
            self.controller.handleError(rpc_pb.BAD_REQUEST_PROTO,
                                   e.message)

    def tryToOpenSocket(self):
        if self.controller.failed():
            return

        # Open socket
        try:
            self.sock = self.channel.openSocket(self.channel.host,\
                                                self.channel.port)
        except error.UnknownHostError, e:
            self.controller.handleError(rpc_pb.UNKNOWN_HOST,\
                                        e.message)
        except error.IOError, e:
            self.controller.handleError(rpc_pb.IO_ERROR, e.message)

    def tryToSendRpcRequest(self, method, request):
        if self.controller.failed():
            return

        # Create an RPC request protobuf
        rpcRequest = self.channel.createRpcRequest(method, request)

        # Send the request over the socket
        try:
            self.channel.sendRpcMessage(self.sock, rpcRequest)
        except error.IOError, e:
            self.controller.handleError(rpc_pb.IO_ERROR, e.message)

    def tryToReceiveReply(self):
        if self.controller.failed():
            return

        # Get the reply
        try:
            self.byte_stream = self.channel.recvRpcMessage(self.sock)
        except error.IOError, e:
            self.controller.handleError(rpc_pb.IO_ERROR, e.message)

    def tryToParseReply(self):
        if self.controller.failed():
            return

        #Parse RPC reply
        try:
            self.rpcResponse = self.channel.parseResponse(self.byte_stream,
                                                   rpc_pb.Response)
        except error.BadResponseProtoError, e:
            self.controller.handleError(rpc_pb.BAD_RESPONSE_PROTO, e.message)

    def tryToRetrieveServiceResponse(self, response_class):
        if self.controller.failed():
            return

        if self.rpcResponse.HasField('error'):
            self.controller.handleError(self.rpcResponse.error_reason,
                                        self.rpcResponse.error)
        else:

            # Extract service response
            try:
                self.serviceResponse = self.channel.parseResponse(
                    self.rpcResponse.response_proto, response_class)
            except error.BadResponseProtoError, e:
                self.controller.handleError(rpc_pb.BAD_RESPONSE_PROTO,
                                            e.message)

    def tryToRunCallback(self, done):
        if self.controller.failed():
            return

        # Check for any outstanding errors
        if(self.rpcResponse.error):
            self.controller.handleError(self.rpcResponse.error_reason,
                                        self.rpcResponse.error)

        # Run the callback, if there is one
        if done:
            done.run(self.serviceResponse)
