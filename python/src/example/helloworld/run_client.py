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
run_client.py - A simple front-end to demo the RPC client implementation.

Authors: Eric Saunders (esaunders@lcogt.net)
         Martin Norbury (mnorbury@lcogt.net)
         Zach Walker (zwalker@lcogt.net)
         Jan Dittberner (jan@dittberner.info)

May 2009, Nov 2010
'''

# Add main protobuf module to classpath
import sys
sys.path.append('../../main')
import traceback

# Import required RPC modules
import hello_world_pb2
from protobuf.socketrpc import RpcService

# Configure logging
import logging
log = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG)

# Server details
hostname = 'localhost'
port = 8090


# Create a request
request = hello_world_pb2.HelloRequest()
request.my_name = 'Zach'

# Create a new service instance
service = RpcService(hello_world_pb2.HelloWorldService_Stub,
                     port,
                     hostname)


def callback(request, response):
    """Define a simple async callback."""
    log.info('Asynchronous response :' + response.__str__())

# Make an asynchronous call
try:
    log.info('Making asynchronous call')
    response = service.HelloWorld(request, callback=callback)
except Exception, ex:
    log.exception(ex)

# Make a synchronous call
try:
    log.info('Making synchronous call')
    response = service.HelloWorld(request, timeout=10000)
    log.info('Synchronous response: ' + response.__str__())
except Exception, ex:
    log.exception(ex)
