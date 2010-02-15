// Copyright (c) 2010 Shardul Deo
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

package com.googlecode.protobuf.socketrpc;

import java.io.IOException;
import java.net.UnknownHostException;

import com.google.protobuf.MessageLite;

/**
 * Abstraction for creating client/server connection through which protocol
 * buffers can be sent and received.
 *
 * @author Shardul Deo
 */
public interface RpcConnectionFactory {

  /**
   * Create a connection over which an rpc can be performed. Note that only one
   * rpc should be performed over the connection returned. i.e.
   * {@link Connection#sendProtoMessage(MessageLite)} and
   * {@link Connection#receiveProtoMessage(MessageLite.Builder)} can be called
   * just once.
   */
  Connection createConnection() throws UnknownHostException, IOException;

  /**
   * Client/Server connection for performing an rpc.
   */
  public interface Connection {

    /**
     * Send the given protocol buffer message over the connection.
     */
    void sendProtoMessage(MessageLite message) throws IOException;

    /**
     * Receive a protocol buffer message over the connection and parse it into
     * the given builder. Note that this method will block until the message is
     * received fully or some error occurs.
     */
    void receiveProtoMessage(MessageLite.Builder messageBuilder)
        throws IOException;

    /**
     * Close the connection and do any cleanup if needed. The connection cannot
     * be used after this method has been called.
     */
    void close() throws IOException;
  }
}
