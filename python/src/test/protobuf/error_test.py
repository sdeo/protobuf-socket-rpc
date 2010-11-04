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
error_test.py - unit tests for the protobuf.error module.

Authors: Eric Saunders (esaunders@lcogt.net)
         Martin Norbury (mnorbury@lcogt.net)
         Jan Dittberner (jan@dittberner.info)

May 2009, Nov 2010
'''

# Modify path to include protobuf
import sys
sys.path.append('../../main')

# Standard library imports
import unittest

# Import the class to test
import protobuf.socketrpc.error as error

# Module imports
import protobuf.socketrpc.rpc_pb2 as rpc_pb2


class TestServerError(unittest.TestCase):
    '''Unit tests for the protobuf.server.ServerError subclasses.'''

    def setUp(self):
        self.msg = 'This is an error message.'

    def tearDown(self):
        pass

    def test_ProtobufError(self):
        expected_error_code = 1

        e = error.ProtobufError(self.msg, expected_error_code)

        self.assertEqual(e.message, self.msg, 'ProtobufError - message')
        self.assertEqual(e.rpc_error_code, expected_error_code,
                         'ProtobufError - error code')

    def test_BadRequestDataError(self):
        expected_error_code = rpc_pb2.BAD_REQUEST_DATA

        e = error.BadRequestDataError(self.msg)

        self.assertEqual(e.message, self.msg, 'BadRequestDataError - message')
        self.assertEqual(e.rpc_error_code, expected_error_code,
                         'BadRequestDataError - error code')

    def test_BadRequestProtoError(self):
        expected_error_code = rpc_pb2.BAD_REQUEST_PROTO

        e = error.BadRequestProtoError(self.msg)

        self.assertEqual(e.message, self.msg, 'BadRequestProtoError - message')
        self.assertEqual(e.rpc_error_code, expected_error_code,
                         'BadRequestProtoError - error code')

    def test_ServiceNotFoundError(self):
        expected_error_code = rpc_pb2.SERVICE_NOT_FOUND

        e = error.ServiceNotFoundError(self.msg)

        self.assertEqual(e.message, self.msg, 'ServiceNotFoundError - message')
        self.assertEqual(e.rpc_error_code, expected_error_code,
                         'ServiceNotFoundError - error code')

    def test_MethodNotFoundError(self):
        expected_error_code = rpc_pb2.METHOD_NOT_FOUND

        e = error.MethodNotFoundError(self.msg)

        self.assertEqual(e.message, self.msg, 'MethodNotFoundError - message')
        self.assertEqual(e.rpc_error_code, expected_error_code,
                         'MethodNotFoundError - error code')

    def test_RpcError(self):
        expected_error_code = rpc_pb2.RPC_ERROR

        e = error.RpcError(self.msg)

        self.assertEqual(e.message, self.msg, 'RpcError - message')
        self.assertEqual(e.rpc_error_code, expected_error_code,
                         'RpcError - error code')

    def test_RpcFailed(self):
        expected_error_code = rpc_pb2.RPC_FAILED

        e = error.RpcFailed(self.msg)

        self.assertEqual(e.message, self.msg, 'RpcFailed - message')
        self.assertEqual(e.rpc_error_code, expected_error_code,
                         'RpcFailed - error code')

    def test_InvalidRequestProtoError(self):
        expected_error_code = rpc_pb2.INVALID_REQUEST_PROTO

        e = error.InvalidRequestProtoError(self.msg)

        self.assertEqual(e.message, self.msg, 'InvalidRequestProtoError - \
                                               message')
        self.assertEqual(e.rpc_error_code, expected_error_code,
                         'InvalidRequestProtoError - error code')

    def test_BadResponseProtoError(self):
        expected_error_code = rpc_pb2.BAD_RESPONSE_PROTO

        e = error.BadResponseProtoError(self.msg)

        self.assertEqual(
            e.message, self.msg, 'BadResponseProtoError - message')
        self.assertEqual(e.rpc_error_code, expected_error_code,
                         'BadResponseProtoError - error code')

    def test_UnknownHostError(self):
        expected_error_code = rpc_pb2.UNKNOWN_HOST

        e = error.UnknownHostError(self.msg)

        self.assertEqual(e.message, self.msg, 'UnknownHostError - message')
        self.assertEqual(e.rpc_error_code, expected_error_code,
                         'UnknownHostError - error code')

    def test_IOError(self):
        expected_error_code = rpc_pb2.IO_ERROR

        e = error.IOError(self.msg)

        self.assertEqual(e.message, self.msg, 'IOError - message')
        self.assertEqual(e.rpc_error_code, expected_error_code,
                         'IOError - error code')


def suite():
    '''Return the test suite containing all tests from this module.'''

    suite = unittest.TestSuite()
    suite.addTest(unittest.makeSuite(TestServerError))
    return suite


if __name__ == '__main__':
    unittest.main()
