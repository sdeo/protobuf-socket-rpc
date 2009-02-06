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

import sdeo.test.rpc.AddressBookImpl;
import sdeo.test.rpc.AddressBookProtos.AddressBook;

import com.google.protobuf.Message;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.Service;
import com.google.protobuf.Descriptors.MethodDescriptor;

/**
 * Socket server for running rpc services.
 * 
 * @author Shardul Deo
 */
public class SocketRpcServer {

  private static final Logger LOG = 
    Logger.getLogger(SocketRpcServer.class.getName());
  
  private final Map<String, Service> serviceMap = new HashMap<String, Service>();
  private final ExecutorService executor;
  private final int port;

  public SocketRpcServer(int port, ExecutorService executorService) {
    this.port = port;
    this.executor = executorService;
  }

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
        callMethod(in, out);
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

    private void callMethod(InputStream in,
        final OutputStream out) throws IOException {
      // Get the service/method
      SocketRpcProtos.Request rpcRequest = SocketRpcProtos.Request.newBuilder()
          .mergeFrom(in).build();
      Service service = serviceMap.get(rpcRequest.getServiceName());
      if (service == null) {
        // TODO Send back error
        LOG.warning("Could not find service: " + rpcRequest.getServiceName());
        return;
      }
      MethodDescriptor method = service.getDescriptorForType()
          .findMethodByName(rpcRequest.getMethodName());
      if (method == null) {
        // TODO Send back error
        LOG.warning("Could not find method: " + rpcRequest.getMethodName());
        return;
      }
      
      // Call method, read/write request/response
      // TODO Return error if bad type
      Message request = service.getRequestPrototype(method).newBuilderForType()
          .mergeFrom(rpcRequest.getRequestProto()).build();
      SocketRpcController socketController = new SocketRpcController();
      Callback callback = new Callback();
      service.callMethod(method, socketController, request, callback);
      callback.response.writeTo(out);
      out.flush();
    }
    
    /**
     * Callback that just saves the response.
     */
    private class Callback implements RpcCallback<Message> {

      private Message response;
      
      @Override
      public void run(Message response) {
        this.response = response;
      }
    }
  }
}
