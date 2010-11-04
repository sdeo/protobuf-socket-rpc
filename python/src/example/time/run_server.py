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
run_server.py - An example server using the python socket
implementation of the Google Protocol Buffers.

This module is an executable script demonstrating the usage of the
python socket implementation of the Google Protocol Buffers. This
script starts a socket server running on localhost:8090. Once running,
the run_client.py script can be used to test the TimeService.getTime
remote procedure call (RPC).

Authors: Martin Norbury (mnorbury@lcogt.net)
         Eric Saunders (esaunders@lcogt.net)
         Jan Dittberner (jan@dittberner.info)

May 2009, Nov 2010
'''

# Add main protobuf module to classpath
import sys
sys.path.append('../../main')

from protobuf.socketrpc.server import SocketRpcServer

import time_pb2 as proto
import time
import logging

log = logging.getLogger(__name__)
port = 8090


class TimeService(proto.TimeService):
    '''An example service implementation.'''

    def getTime(self, controller, request, done):
        '''Get the current time and return as a response message via the
        callback routine provide.'''
        log.info('Called TestMethod')

        # Create response message
        response = proto.TimeResponse()
        response.str_time = time.asctime()

        # Call provided callback with response message
        done.run(response)


if __name__ == '__main__':
    logging.basicConfig(level=logging.DEBUG)

    # Create service
    service = TimeService()
    server = SocketRpcServer(port)
    server.registerService(service)
    server.run()
