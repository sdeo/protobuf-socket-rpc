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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcChannel;
import com.google.protobuf.Service;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.googlecode.protobuf.socketrpc.SocketRpcProtos.Response.Builder;
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

  private final Map<String, Service> serviceMap =
    new HashMap<String, Service>();
  private final ExecutorService executor;
  private final int port;
  private final int backlog;
  private final InetAddress bindAddr;

  /**
   * @param port Port that this server will be started on.
   * @param executorService To be used for handling requests.
   */
  public SocketRpcServer(int port, ExecutorService executorService) {
    this.port = port;
    this.executor = executorService;
    this.backlog = 0;	// the default will be taken.
    this.bindAddr = null;
  }

	/**
	 * Constructor with customization to pass into
	 * java.net.ServerSocket(int port, int backlog, InetAddress bindAddr)
	 *
	 * @param port
	 *            Port that this server will be started on.
	 * @param backlog
	 *            the maximum length of the queue. A value <=0 uses default
	 *            backlog.
	 * @param bindAddr
	 *            the local InetAddress the server will bind to. A null value
	 *            binds to any/all local ip addresses.
	 * @param executorService
	 *            executorService To be used for handling requests.
	 */
	public SocketRpcServer(int port, int backlog, InetAddress bindAddr,
			ExecutorService executorService) {
		this.port = port;
		this.executor = executorService;
		this.backlog = backlog;
		this.bindAddr = bindAddr;
	}

  /**
   * Register an rpc service implementation on this server.
   */
  public void registerService(Service service) {
    serviceMap.put(service.getDescriptorForType().getFullName(), service);
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

      // Get the service/method
      Service service = serviceMap.get(rpcRequest.getServiceName());
      if (service == null) {
        return handleError("Could not find service: "
            + rpcRequest.getServiceName(), ErrorReason.SERVICE_NOT_FOUND,
            null);
      }
      MethodDescriptor method = service.getDescriptorForType()
          .findMethodByName(rpcRequest.getMethodName());
      if (method == null) {
        return handleError(
            String.format("Could not find method %s in service %s",
                rpcRequest.getMethodName(),
                service.getDescriptorForType().getFullName()),
            ErrorReason.METHOD_NOT_FOUND, null);
      }

      // Parse request
      Message.Builder builder;
      try {
        builder = service.getRequestPrototype(method).newBuilderForType()
            .mergeFrom(rpcRequest.getRequestProto());
        if (!builder.isInitialized()) {
          return handleError("Invalid request proto",
              ErrorReason.BAD_REQUEST_PROTO, null);
        }
      } catch (InvalidProtocolBufferException e) {
        return handleError("Invalid request proto",
            ErrorReason.BAD_REQUEST_PROTO, e);
      }
      Message request = builder.build();

      // Call method
      SocketRpcController socketController = new SocketRpcController();
      socketController.success = true;
      Callback callback = new Callback();
      try {
        service.callMethod(method, socketController, request, callback);
      } catch (RuntimeException e) {
        return handleError("Error running method " + method.getFullName()
            + rpcRequest.getMethodName(), ErrorReason.RPC_ERROR, e);
      }

      // Build and return response (callback is optional)
      Builder responseBuilder = SocketRpcProtos.Response.newBuilder();
      if (callback.response != null) {
        responseBuilder.setCallback(true).setResponseProto(
            callback.response.toByteString());
      } else {
        // Set whether callback was called
        responseBuilder.setCallback(callback.invoked);
      }
      if (!socketController.success) {
        responseBuilder.setError(socketController.error);
        responseBuilder.setErrorReason(ErrorReason.RPC_FAILED);
      }
      return responseBuilder.build();
    }

    private SocketRpcProtos.Response handleError(String msg,
        ErrorReason reason, Exception e) {
      LOG.log(Level.WARNING, reason + ": " + msg, e);
      return SocketRpcProtos.Response
          .newBuilder()
          .setError(msg)
          .setErrorReason(reason)
          .build();
    }

    /**
     * Callback that just saves the response and the fact that it was invoked.
     */
    private class Callback implements RpcCallback<Message> {

      private Message response;
      private boolean invoked = false;

      public void run(Message response) {
        this.response = response;
        invoked = true;
      }
    }
  }
}
