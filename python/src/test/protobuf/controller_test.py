#!/usr/bin/python
# Copyright (c) 2009 Las Cumbres Observatory (www.lcogt.net)
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

'''controller_test.py - unit tests for the protobuf.controller module.

Authors: Eric Saunders (esaunders@lcogt.net)
         Martin Norbury (mnorbury@lcogt.net)

May 2009
'''

# Standard library imports
import unittest

# Add protobuf module to the classpath
import sys
sys.path.append('../../main')

# Import the class to test
import protobuf.controller as controller

# Import the fake stub classes used in testing to simulate sockets etc.
from fake import FakeSocketFactory, FakeSocket, FakeCallback, TestServiceImpl 

# Import the RPC definition class and the test service class
import protobuf.rpc_pb2 as rpc_pb2
import test_pb2



class TestSocketRpcController(unittest.TestCase):
    '''Unit tests for the protobuf.channel.SocketRpcController class.'''

    def setUp(self):
        self.controller = controller.SocketRpcController()

    def tearDown(self):
        pass

    def test___init__(self):
        '''Test SocketRpcController constructor.'''
    
        self.assertEqual(self.controller.success, False,
                         "Attribute 'success' incorrectly initialized")

        self.assertEqual(self.controller.error, None,
                         "Attribute 'error' incorrectly initialized")

        self.assertEqual(self.controller.reason, None,
                         "Attribute 'reason' incorrectly initialized")
    

    def test_handleError(self):
        '''Test handleError - normal usage.'''
    
        # Set the controller state to see if it is changed correctly
        self.controller.success = True
        
        # Create an error code and message to pass in
        error_code = '4'
        message    = 'Chips are soggy'
        
        self.controller.handleError(error_code, message)

        self.assertEqual(self.controller.success, False,
                         "handleError - Attribute 'success'")
                         
        self.assertEqual(self.controller.reason, error_code,
                         "handleError - Attribute 'reason'")

        self.assertEqual(self.controller.error, message,
                         "handleError - Attribute 'error'")


    def test_reset(self):
        '''Test reset - normal usage.'''
    
        # Set the controller state to see if it is changed correctly
        self.controller.success    = True
        self.controller.error_code = '4'
        self.controller.reason     = 'Chips are soggy'

        self.controller.reset()
        
        self.assertEqual(self.controller.success, False,
                         "reset - Attribute 'success'")

        self.assertEqual(self.controller.error, None,
                         "reset - Attribute 'error'")

        self.assertEqual(self.controller.reason, None,
                         "reset - Attribute 'reason'")


    def test_failed(self):
        '''Test failed - normal usage.'''
    
        self.assertEqual(self.controller.failed(), True, 
                         "failed - failed state")

        self.controller.success = True

        self.assertEqual(self.controller.failed(), False, 
                         "failed - success state")


def suite():
    '''Return the test suite containing all tests from this module.'''
    
    suite = unittest.TestSuite()
    suite.addTest(unittest.makeSuite(TestSocketRpcController))
    
    return suite


if __name__ == '__main__':
    unittest.main()
