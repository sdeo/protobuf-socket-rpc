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
run_client.py - An example client using the python socket
implementation of the Google Protocol Buffers.

This module is an executable script demonstrating the usage of the
python socket implementation of the Google Protocol Buffers. To work
correctly, the script requires a server to be running first
(i.e. run_server.py).

Authors: Martin Norbury (mnorbury@lcogt.net)
         Eric Saunders (esaunders@lcogt.net)
         Zach Walker (zwalker@lcogt.net)
         Jan Dittberner (jan@dittberner.info)

May 2009, Nov 2010
'''

# Add main protobuf module to classpath
import sys
sys.path.append('../../main')

import time_pb2 as proto
from protobuf.socketrpc import RpcService

import logging
log = logging.getLogger(__name__)
hostname = 'localhost'
port = 8090


if __name__ == '__main__':
    logging.basicConfig(level=logging.DEBUG)
    log.debug("test")

    # Create request message
    request = proto.TimeRequest()

    service = RpcService(proto.TimeService_Stub, port, hostname)
    try:
        response = service.getTime(request, timeout=1000)
        log.info(response)
    except Exception, ex:
        log.exception(ex)
