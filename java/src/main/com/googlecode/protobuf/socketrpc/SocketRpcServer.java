// Copyright (c) 2009 Shardul Deo
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.protobuf.BlockingService;
import com.google.protobuf.RpcChannel;
import com.google.protobuf.Service;
import com.googlecode.protobuf.socketrpc.RpcForwarder.RpcException;
import com.googlecode.protobuf.socketrpc.SocketRpcProtos.ErrorReason;

/**
 * Socket server for running rpc services. It can serve requests for any
 * registered service from any client who is using {@link RpcChannel}.
 * <p>
 * Note that this server can only handle synchronous requests, so the client is
 * blocked until the callback is called by the service implementation.
 *
 * @author Shardul Deo
 */
public class SocketRpcServer {

  private static final Logger LOG =
    Logger.getLogger(SocketRpcServer.class.getName());

  private final RpcForwarder rpcForwarder;
  private final ExecutorService executor;
  private final int port;
  private final int backlog;
  private final InetAddress bindAddr;

  /**
   * @param port Port that this server will be started on.
   * @param executorService To be used for handling requests.
   */
  public SocketRpcServer(int port, ExecutorService executorService) {
    this(port, 0, null, executorService);
  }

  /**
   * Constructor with customization to pass into java.net.ServerSocket(int port,
   * int backlog, InetAddress bindAddr)
   *
   * @param port
   *          Port that this server will be started on.
   * @param backlog
   *          the maximum length of the queue. A value <=0 uses default backlog.
   * @param bindAddr
   *          the local InetAddress the server will bind to. A null value binds
   *          to any/all local ip addresses.
   * @param executorService
   *          executorService To be used for handling requests.
   */
	public SocketRpcServer(int port, int backlog, InetAddress bindAddr,
			ExecutorService executorService) {
	  this.rpcForwarder = new RpcForwarder();
		this.port = port;
		this.executor = executorService;
		this.backlog = backlog;
		this.bindAddr = bindAddr;
	}

  /**
   * Register an RPC service implementation on this server.
   */
  public void registerService(Service service) {
    rpcForwarder.registerService(service);
  }

  /**
   * Register an RPC blocking service implementation on this server.
   */
  public void registerBlockingService(BlockingService service) {
    rpcForwarder.registerBlockingService(service);
  }

  /**
   * Start the server to listen for requests. This thread is blocked.
   *
   * @throws IOException
   *           If there was an error starting up the server.
   */
  public void run() throws IOException {
    ServerSocket serverSocket = new ServerSocket(port, backlog, bindAddr);

    LOG.info("Listening for requests on port: " + port);
    try {
      while (true) {
        // Thread blocks here waiting for requests
        executor.execute(new Handler(serverSocket.accept()));
      }
    } catch (IOException ex) {
      LOG.info("Shutting down server");
      executor.shutdown();
      serverSocket.close();
    }
  }

  /**
   * Handles socket requests.
   */
  class Handler implements Runnable {

    private final Socket socket;

    Handler(Socket socket) {
      this.socket = socket;
    }

    public void run() {
      // Get streams from socket
      OutputStream out = null;
      InputStream in = null;
      try {
        in = new BufferedInputStream(socket.getInputStream());
        out = new BufferedOutputStream(socket.getOutputStream());
      } catch (IOException e) {
        LOG.log(Level.WARNING, "Error while opening I/O", e);
        cleanUp(in, out);
        return;
      }

      // Call method
      try {
        SocketRpcProtos.Response rpcResponse = callMethod(in);
        rpcResponse.writeTo(out);
        out.flush();
        socket.shutdownOutput();
      } catch (IOException e) {
        LOG.log(Level.WARNING, "Error while reading/writing", e);
      } finally {
        cleanUp(in, out);
      }
    }

    private void cleanUp(InputStream in,
        final OutputStream out) {
      try {
        if (in != null) {
          in.close();
        }
        if (out != null) {
          out.close();
        }
        socket.close();
      } catch (IOException e) {
        // It's ok
        LOG.log(Level.WARNING, "Error while closing I/O", e);
      }
    }

    private SocketRpcProtos.Response callMethod(InputStream in) {
      // Parse request
      SocketRpcProtos.Request rpcRequest;
      try {
        SocketRpcProtos.Request.Builder builder = SocketRpcProtos.Request
            .newBuilder().mergeFrom(in);
        if (!builder.isInitialized()) {
          return handleError("Invalid request from client",
              ErrorReason.BAD_REQUEST_DATA, null);
        }
        rpcRequest = builder.build();
      } catch (IOException e) {
        return handleError("Bad request data from client",
            ErrorReason.BAD_REQUEST_DATA, e);
      }

      try {
        return rpcForwarder.doRPC(rpcRequest);
      } catch (RpcException e) {
        return handleError(e.msg, e.errorReason, e.getCause());
      }
    }

    private SocketRpcProtos.Response handleError(String msg,
        ErrorReason reason, Throwable throwable) {
      LOG.log(Level.WARNING, reason + ": " + msg, throwable);
      return SocketRpcProtos.Response
          .newBuilder()
          .setError(msg)
          .setErrorReason(reason)
          .build();
    }
  }
}
