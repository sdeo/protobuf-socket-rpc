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
run_server.py - A simple front-end to demo the RPC server implementation.

Author: Eric Saunders (esaunders@lcogt.net)
        Jan Dittberner (jan@dittberner.info)

May 2009, Nov 2010
'''

# Add main protobuf module to classpath
import sys
sys.path.append('../../main')

# Import required RPC modules
import hello_world_pb2
import protobuf.socketrpc.server as server
import HelloWorldServiceImpl as impl


# Create and register the service
# Note that this is an instantiation of the implementation class,
# *not* the class defined in the proto file.
hello_world_service = impl.HelloWorldImpl()
server = server.SocketRpcServer(8090)
server.registerService(hello_world_service)

# Start the server
print 'Serving on port 8090'
server.run()
