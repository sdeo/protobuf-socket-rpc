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

'''run_client.py - A simple front-end to demo the RPC client implementation.

Authors: Eric Saunders (esaunders@lcogt.net)
         Martin Norbury (mnorbury@lcogt.net)

May 2009
'''

# Add main protobuf module to classpath
import sys
sys.path.append('../../main')

# Import required RPC modules
import hello_world_pb2
import protobuf.channel as ch

# Configure logging
import logging
log = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG)

# Server details
hostname = 'localhost'
port     = 8090


# Define a callback class
class Callback:
    def run(self,response):
        self.data = response.hello_world

# Create a request
request = hello_world_pb2.HelloRequest()
request.my_name = 'Eric'

# Create the channel and controller
channel    = ch.SocketRpcChannel(hostname,port)
controller = channel.newController()

# Execute the service on the remote service
callback = Callback()
service  = hello_world_pb2.HelloWorldService_Stub(channel)
service.HelloWorld(controller,request,callback)

# Check for failure
if controller.failed():
    log.error(controller.success)
    log.error(controller.error)
    log.error(controller.reason)
else:
    log.info(callback.data)

