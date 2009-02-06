package com.googlecode.protobuf.socketrpc;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcChannel;
import com.google.protobuf.Service;
import com.google.protobuf.UninitializedMessageException;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.googlecode.protobuf.socketrpc.SocketRpcProtos.Response.Builder;

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
  
  private final Map<String, Service> serviceMap = new HashMap<String, Service>();
  private final ExecutorService executor;
  private final int port;

  /**
   * @param port Port that this server will be started on.
   * @param executorService To be used for handling requests.
   */
  public SocketRpcServer(int port, ExecutorService executorService) {
    this.port = port;
    this.executor = executorService;
  }

  /**
   * Register an rpc service implementation on this server.
   */
  public void registryService(Service service) {
    serviceMap.put(service.getDescriptorForType().getFullName(), service);
  }

  /**
   * Start the server to listen for requests. This thread is blocked.
   * 
   * @throws IOException
   *           If there was an error starting up the server.
   */
  public void run() throws IOException {
    ServerSocket serverSocket = new ServerSocket(port);

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
  private class Handler implements Runnable {

    private final Socket socket;

    private Handler(Socket socket) {
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

    private SocketRpcProtos.Response callMethod(InputStream in)
        throws IOException {
      // Parse request
      SocketRpcProtos.Request rpcRequest;
      try {
        rpcRequest = SocketRpcProtos.Request.newBuilder().mergeFrom(in).build();
      } catch (UninitializedMessageException e) {
        return handleError("Bad request from client", e);
      }
      
      // Get the service/method
      Service service = serviceMap.get(rpcRequest.getServiceName());
      if (service == null) {
        return handleError("Could not find service: "
            + rpcRequest.getServiceName(), null);
      }
      MethodDescriptor method = service.getDescriptorForType()
          .findMethodByName(rpcRequest.getMethodName());
      if (method == null) {
        return handleError(String.format(
            "Could not find method %s in service %s", 
            rpcRequest.getMethodName(), 
            service.getDescriptorForType().getFullName()), null);
      }
      
      // Parse request
      Message request;
      try {
        request = service.getRequestPrototype(method).newBuilderForType()
            .mergeFrom(rpcRequest.getRequestProto()).build();
      } catch (UninitializedMessageException e) {
        return handleError("Invalid request proto", e);
      }
      
      // Call method
      SocketRpcController socketController = new SocketRpcController();
      Callback callback = new Callback();
      try {
        service.callMethod(method, socketController, request, callback);
      } catch (RuntimeException e) {
        return handleError("Error running method " + method.getFullName()
            + rpcRequest.getMethodName(), e);
      }
      
      // Build and return response (callback is optional)
      Builder responseBuilder = SocketRpcProtos.Response.newBuilder();
      if (callback.response != null) {
        responseBuilder.setResponseProto(callback.response.toByteString());
      } else if (callback.invoked) {
        // Callback with null
        responseBuilder.setResponseProto(ByteString.EMPTY);
      }
      if (socketController.error != null) {
        responseBuilder.setError(socketController.error);
      }
      return responseBuilder.build();
    }
    
    private SocketRpcProtos.Response handleError(String msg, Exception e) {
      LOG.log(Level.WARNING, msg, e);
      return SocketRpcProtos.Response
          .newBuilder()
          .setError(msg)
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
