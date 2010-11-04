#!/usr/bin/python
# Copyright (c) 2009 Las Cumbres Observatory  (www.lcogt.net)
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

'''run_tests.py - Run all unit tests for the protobuf.* package.

Authors: Eric Saunders (esaunders@lcogt.net)
         Martin Norbury (mnorbury@lcogt.net)

May 2009
'''

# Standard library imports
import unittest

# Module imports
import channel_test
import controller_test
import error_test
import server_test
import service_test

if __name__ == '__main__':
    suite = unittest.TestSuite()
    suite.addTest(channel_test.suite())
    suite.addTest(controller_test.suite())
    suite.addTest(error_test.suite())
    suite.addTest(server_test.suite())
    suite.addTest(service_test.suite())

    unittest.TextTestRunner(verbosity=0).run(suite)
