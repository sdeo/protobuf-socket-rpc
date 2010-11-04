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
error.py - Exception classes for the protobuf package.

This package contains exception classes mapped to the rpc.proto file.

Authors: Martin Norbury (mnorbury@lcogt.net)
         Eric Saunders (esaunders@lcogt.net)
         Jan Dittberner (jan@dittberner.info)

May 2009, Nov 2010
'''

# Module imports
from protobuf.socketrpc import rpc_pb2 as rpc_pb


class ProtobufError(Exception):
    '''Base exception class for RPC protocol buffer errors.'''

    def __init__(self, message, rpc_error_code):
        '''ProtobufError constructor.

        message - Message string detailing error.
        rpc_error_code - Error code from rpc.proto file.
        '''
        self.message = message
        self.rpc_error_code = rpc_error_code


class BadRequestDataError(ProtobufError):
    '''Exception generated for a BadRequestDataError.'''

    def __init__(self, message):
        super(BadRequestDataError, self).__init__(
            message, rpc_pb.BAD_REQUEST_DATA)


class BadRequestProtoError(ProtobufError):
    '''Exception generated for a BadRequestProtoError.'''

    def __init__(self, message):
        super(BadRequestProtoError, self).__init__(
            message, rpc_pb.BAD_REQUEST_PROTO)


class ServiceNotFoundError(ProtobufError):
    '''Exception generated for a ServiceNotFoundError.'''

    def __init__(self, message):
        super(ServiceNotFoundError, self).__init__(
            message, rpc_pb.SERVICE_NOT_FOUND)


class MethodNotFoundError(ProtobufError):
    '''Exception generated for a MethodNotFoundError.'''

    def __init__(self, message):
        super(MethodNotFoundError, self).__init__(
            message, rpc_pb.METHOD_NOT_FOUND)


class RpcError(ProtobufError):
    '''Exception generated for an RpcError.'''

    def __init__(self, message):
        super(RpcError, self).__init__(message, rpc_pb.RPC_ERROR)


class RpcFailed(ProtobufError):
    '''Exception generated for an RpcFailed.'''

    def __init__(self, message):
        super(RpcFailed, self).__init__(message, rpc_pb.RPC_FAILED)


class InvalidRequestProtoError(ProtobufError):
    '''Exception generated for an InvalidRequestProtoError.'''

    def __init__(self, message):
        super(InvalidRequestProtoError, self).__init__(
            message, rpc_pb.INVALID_REQUEST_PROTO)


class BadResponseProtoError(ProtobufError):
    '''Exception generated for a BadResponseProtoError.'''

    def __init__(self, message):
        super(BadResponseProtoError, self).__init__(
            message, rpc_pb.BAD_RESPONSE_PROTO)


class UnknownHostError(ProtobufError):
    '''Exception generated for an UnknownHostError.'''

    def __init__(self, message):
        super(UnknownHostError, self).__init__(message, rpc_pb.UNKNOWN_HOST)


class IOError(ProtobufError):
    '''Exception generated for an IOError.'''

    def __init__(self, message):
        super(IOError, self).__init__(message, rpc_pb.IO_ERROR)
