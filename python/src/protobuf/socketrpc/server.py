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
server.py - RPC server implementation for Google's Protocol Buffers.

This package provides classes to handle the server side of an RPC
transaction, that implements Shardul Deo's RPC protocol, using
Protocol Buffers for the data interchange format. See

http://code.google.com/p/protobuf-socket-rpc/
http://code.google.com/p/protobuf/

for more information.

Authors: Martin Norbury (mnorbury@lcogt.net)
         Eric Saunders (esaunders@lcogt.net)
         Jan Dittberner (jan@dittberner.info)

May 2009, Nov 2010
'''

# Standard library imports
import SocketServer
import threading
import logging
import socket

# Third-party imports

# Module imports
from protobuf.socketrpc import rpc_pb2 as rpc_pb
from protobuf.socketrpc.controller import SocketRpcController
from protobuf.socketrpc import error


class NullHandler(logging.Handler):
    '''A null logging handler to prevent clients that don't require the
    logging package from reporting no handlers found.'''
    def emit(self, record):
        pass

log = logging.getLogger(__name__)
log.addHandler(NullHandler())


class Callback():
    '''Class to allow execution of client-supplied callbacks.'''

    def __init__(self):
        self.invoked = False
        self.response = None

    def run(self, response):
        self.response = response
        self.invoked = True


class SocketHandler(SocketServer.StreamRequestHandler):
    '''Handler for service requests.'''

    def __init__(self, request, client_address, server, socket_rpc_server):
        self.socket_rpc_server = socket_rpc_server
        SocketServer.StreamRequestHandler.__init__(
            self, request, client_address, server)

    def handle(self):
        '''Entry point for handler functionality.'''
        log.debug('Got a request')

        # Parse the incoming request
        recv = self.rfile.read()

        # Evaluate and execute the request
        rpcResponse = self.validateAndExecuteRequest(recv)
        log.debug("Response to return to client \n %s" % rpcResponse)

        # Send reply to client
        self.wfile.write(rpcResponse.SerializeToString())
        self.request.shutdown(socket.SHUT_RDWR)

    def validateAndExecuteRequest(self, input):
        '''Match a client request to the corresponding service and method on
        the server, and then call the service.'''

        # Parse and validate the client's request
        try:
            request = self.parseServiceRequest(input)
        except error.BadRequestDataError, e:
            return self.handleError(e)

        # Retrieve the requested service
        try:
            service = self.retrieveService(request.service_name)
        except error.ServiceNotFoundError, e:
            return self.handleError(e)

        # Retrieve the requested method
        try:
            method = self.retrieveMethod(service, request.method_name)
        except error.MethodNotFoundError, e:
            return self.handleError(e)

        # Retrieve the protocol message
        try:
            proto_request = self.retrieveProtoRequest(service, method, request)
        except error.BadRequestProtoError, e:
            return self.handleError(e)

        # Execute the specified method of the service with the requested params
        try:
            response = self.callMethod(service, method, proto_request)
        except error.RpcError, e:
            return self.handleError(e)

        return response

    def parseServiceRequest(self, bytestream_from_client):
        '''Validate the data stream received from the client.'''

        # Convert the client request into a PB Request object
        request = rpc_pb.Request()

        # Catch anything which isn't a valid PB bytestream
        try:
            request.MergeFromString(bytestream_from_client)
        except Exception, e:
            raise error.BadRequestDataError("Invalid request from \
                                            client (decodeError): " + str(e))

        # Check the request is correctly initialized
        if not request.IsInitialized():
            raise error.BadRequestDataError("Client request is missing \
                                             mandatory fields")
        log.debug('Request = %s' % request)

        return request

    def retrieveService(self, service_name):
        '''Match the service request to a registered service.'''
        service = self.socket_rpc_server.serviceMap.get(service_name)
        if service is None:
            msg = "Could not find service '%s'" % service_name
            raise error.ServiceNotFoundError(msg)

        return service

    def retrieveMethod(self, service, method_name):
        '''Match the method request to a method of a registered service.'''
        method = service.DESCRIPTOR.FindMethodByName(method_name)
        if method is None:
            msg = "Could not find method '%s' in service '%s'"\
                   % (method_name, service.DESCRIPTOR.name)
            raise error.MethodNotFoundError(msg)

        return method

    def retrieveProtoRequest(self, service, method, request):
        ''' Retrieve the users protocol message from the RPC message'''
        proto_request = service.GetRequestClass(method)()
        try:
            proto_request.ParseFromString(request.request_proto)
        except Exception, e:
            raise error.BadRequestProtoError(unicode(e))

        # Check the request parsed correctly
        if not proto_request.IsInitialized():
            raise error.BadRequestProtoError('Invalid protocol request \
                                              from client')

        return proto_request

    def callMethod(self, service, method, proto_request):
        '''Execute a service method request.'''
        log.debug('Calling service %s' % service)
        log.debug('Calling method %s' % method)

        # Create the controller (initialised to success) and callback
        controller = SocketRpcController()
        callback = Callback()
        try:
            service.CallMethod(method, controller, proto_request, callback)
        except Exception, e:
            raise error.RpcError(unicode(e))

        # Return an RPC response, with payload defined in the callback
        response = rpc_pb.Response()
        if callback.response:
            response.callback = True
            response.response_proto = callback.response.SerializeToString()
        else:
            response.callback = callback.invoked

        # Check to see if controller has been set to not success by user.
        if controller.failed():
            response.error = controller.error()
            response.error_reason = rpc_pb.RPC_FAILED

        return response

    def handleError(self, e):
        '''Produce an RPC response to convey a server error to the client.'''
        msg = "%d : %s" % (e.rpc_error_code, e.message)
        log.error(msg)

        # Create error reply
        response = rpc_pb.Response()
        response.error_reason = e.rpc_error_code
        response.error = e.message
        return response


class ThreadedTCPServer(SocketServer.ThreadingMixIn, SocketServer.TCPServer):
    SocketServer.allow_reuse_address = True

    def __init__(self, server_address, RequestHandlerClass, socket_rpc_server):
        """Constructor.  May be extended, do not override."""
        SocketServer.TCPServer.__init__(
            self, server_address, RequestHandlerClass)
        self.socket_rpc_server = socket_rpc_server

    def finish_request(self, request, client_address):
        """Finish one request by instantiating RequestHandlerClass."""
        self.RequestHandlerClass(
            request, client_address, self, self.socket_rpc_server)


class SocketRpcServer:
    '''Socket server for running rpc services.'''

    def __init__(self, port, host='localhost'):
        '''port - Port this server is started on'''
        self.port = port
        self.host = host
        self.serviceMap = {}

    def registerService(self, service):
        '''Register an RPC service.'''
        self.serviceMap[service.GetDescriptor().full_name] = service

    def run(self):
        '''Activate the server.'''
        log.info('Running server on port %d' % self.port)
        server = ThreadedTCPServer((self.host, self.port), SocketHandler, self)
        server.serve_forever()
