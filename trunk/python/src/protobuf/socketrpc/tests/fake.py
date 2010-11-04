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
fake.py - Fake objects for use in unit testing.

Authors: Martin Norbury (mnorbury@lcogt.net)
         Eric Saunders (esaunders@lcogt.net)
         Jan Dittberner (jan@dittberner.info)

May 2009, Nov 2010
'''

# Standard library imports
import socket
import traceback

# Module imports
import protobuf.socketrpc.channel as ch
import protobuf.socketrpc.rpc_pb2 as rpc_pb2
import test_pb2


class TestServiceImpl(test_pb2.TestService):
    '''Implements a simple service class for use in testing.'''

    def __init__(self, exception=None, failmsg=None):
        # Set some fixed response data
        self.response_str_data = 'And the lord taketh away'
        self.response_int_data = 42

        # Set the exception and fail message, if provided by the user
        self.exception = exception
        self.failmsg = failmsg

    def TestMethod(self, controller, request, done):
        # Raise an exception if one has been passed in
        if self.exception:
            raise self.exception

        # Extract request message contents
        str_data = request.str_data

        # Create a reply
        response = test_pb2.Response()
        response.str_data = self.response_str_data
        response.int_data = self.response_int_data

        # Simulate a user specified failure
        if self.failmsg:
            controller.handleError(-1, self.failmsg)

        # As per specification, call the run method of the done callback
        done.run(response)


class FakeCallback:
    def __init__(self):
        self.response = None
        self.invoked = False

    def run(self, response):
        self.response = response
        self.invoked = True


class FakeSocket:
    '''Stub class implementing a minimal socket interface.'''

    class Makefile:
        '''Stub class implementing the minimal interface of a
        socket StreamRequest.'''

        def __init__(self):
            self.stream_data = None
            self.closed = 0

        def read(self, size=0):
            return self.stream_data

        def write(self, str):
            self.stream_data = str

        def flush(self):
            pass

        def close(self):
            self.closed = 1

    def __init__(self):

        # Initialise the data streams
        self.input_stream = FakeSocket.Makefile()
        self.output_stream = FakeSocket.Makefile()

        # Set the error flags
        self.unknownhost = False
        self.ioerror = False

    def connect(self, (host, port)):
        if self.unknownhost:
            raise socket.gaierror()

        if self.ioerror:
            raise socket.error()

    def makefile(self, mode, buf_size=0):

        #Simulate an IOError on read or write if the flag is set
        if self.ioerror:
            raise socket.error

        if mode == 'w' or mode == 'wb':
            return self.output_stream
        elif mode == 'r' or mode == 'rb':
            return self.input_stream
        else:
            raise NotImplementedError('makefile mode not implemented')

    def shutdown(self, flag):
        pass

    def close(self):
        self.input_stream.close()
        self.output_stream.close()

    def getRequest(self):
        request = rpc_pb2.Request()
        request.MergeFromString(self.output_stream.stream_data)
        return request

    def throwUnknownHostException(self):
        self.unknownhost = True

    def throwIOErrorException(self):
        self.ioerror = True

    def withResponseProto(self, responseproto):
        rpcResponse = rpc_pb2.Response()
        rpcResponse.callback = True
        rpcResponse.response_proto = responseproto.SerializeToString()
        self.input_stream.stream_data = rpcResponse.SerializeToString()

    def withNoResponse(self, callback):
        rpcResponse = rpc_pb2.Response()
        rpcResponse.callback = callback
        self.input_stream.stream_data = rpcResponse.SerializeToString()

    def withInputBytes(self, str):
        self.input_stream.stream_data = str


class FakeSocketFactory(ch.SocketFactory):
    def __init__(self):
        self.socket = FakeSocket()

    def createSocket(self):
        ''' Create the socket '''
        return self.socket

    def setSocket(self, socket):
        ''' Used to override the default fake socket'''
        self.socket = socket
