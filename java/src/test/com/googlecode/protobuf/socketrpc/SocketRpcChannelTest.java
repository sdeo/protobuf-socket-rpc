package com.googlecode.protobuf.socketrpc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.SocketFactory;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.RpcCallback;
import com.googlecode.protobuf.socketrpc.SocketRpcController.ErrorReason;
import com.googlecode.protobuf.socketrpc.TestProtos.Request;
import com.googlecode.protobuf.socketrpc.TestProtos.Response;
import com.googlecode.protobuf.socketrpc.TestProtos.TestService;

import junit.framework.TestCase;

/**
 * Unit tests for {@link SocketRpcChannel}
 *
 * @author Shardul Deo
 */
public class SocketRpcChannelTest extends TestCase {

  private static class FakeSocketFactory extends SocketFactory {

    private Socket socket;

    private FakeSocketFactory returnsSocket(Socket socket) {
      this.socket = socket;
      return this;
    }

    public Socket createSocket() {
      return socket;
    }

    public Socket createSocket(String host, int port) throws IOException,
        UnknownHostException {
      return createSocket();
    }

    public Socket createSocket(InetAddress host, int port) throws IOException {
      return createSocket();
    }

    public Socket createSocket(String host, int port, InetAddress localHost,
        int localPort) throws IOException, UnknownHostException {
      return createSocket();
    }

    public Socket createSocket(InetAddress address, int port,
        InetAddress localAddress, int localPort) throws IOException {
      return createSocket();
    }
  }

  private static class FakeSocket extends Socket {

    private ByteArrayInputStream input;
    private ByteArrayOutputStream output;

    private FakeSocket withInput(byte[] inputBytes) {
      input = new ByteArrayInputStream(inputBytes);
      return this;
    }

    private FakeSocket withResponseProto(Message message) {
      SocketRpcProtos.Response rpcResponse = SocketRpcProtos.Response
          .newBuilder().setResponseProto(message.toByteString()).build();
      input = new ByteArrayInputStream(rpcResponse.toByteArray());
      return this;
    }

    private SocketRpcProtos.Request getRequest()
        throws InvalidProtocolBufferException {
      return SocketRpcProtos.Request.newBuilder().mergeFrom(
          output.toByteArray()).build();
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return input;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
      output = new ByteArrayOutputStream();
      return output;
    }

    @Override
    public void shutdownOutput() throws IOException {
      // no-op
    }

    @Override
    public synchronized void close() throws IOException {
      // no-op
    }
  }

  private static class FakeCallback implements RpcCallback<Response> {

    private Response response;
    private boolean invoked = false;

    public void run(Response response) {
      this.response = response;
      invoked = true;
    }
  };

  public void testGoodRpc() throws InvalidProtocolBufferException {
    // Create data
    String reqdata = "Request Data";
    String resdata = "Response Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();
    Response response = Response.newBuilder().setStrData(resdata).build();

    // Create service
    FakeSocket socket = new FakeSocket().withResponseProto(response);
    SocketRpcChannel rpcChannel = new SocketRpcChannel("host", -1,
        new FakeSocketFactory().returnsSocket(socket));
    SocketRpcController controller = rpcChannel.newRpcController();
    TestService service = TestProtos.TestService.newStub(rpcChannel);

    // Call rpc method
    FakeCallback callback = new FakeCallback();
    service.testMethod(controller, request, callback);

    // Verify request/response to socket
    assertEquals(request.toByteString(), socket.getRequest().getRequestProto());
    assertEquals(TestService.getDescriptor().getFullName(), socket.getRequest()
        .getServiceName());
    assertEquals(TestService.getDescriptor().getMethods().get(0).getName(),
        socket.getRequest().getMethodName());
    assertTrue(callback.invoked);
    assertEquals(resdata, callback.response.getStrData());
  }

  /**
   * Rpc called with bad request proto
   */
  public void testBadRequest() throws InvalidProtocolBufferException {
    // Create data
    String resdata = "Response Data";
    Request request = Request.newBuilder().buildPartial(); // required missing
    Response response = Response.newBuilder().setStrData(resdata).build();

    // Create service
    FakeSocket socket = new FakeSocket().withResponseProto(response);
    SocketRpcChannel rpcChannel = new SocketRpcChannel("host", -1,
        new FakeSocketFactory().returnsSocket(socket));
    SocketRpcController controller = rpcChannel.newRpcController();
    TestService service = TestProtos.TestService.newStub(rpcChannel);

    // Call rpc method
    FakeCallback callback = new FakeCallback();
    service.testMethod(controller, request, callback);

    // Verify
    assertTrue(controller.failed());
    assertEquals(ErrorReason.BadRequestProto, controller.errorReason());
    assertFalse(callback.invoked);
  }

  /**
   * Server responds with bad data
   */
  public void testBadResponse() throws InvalidProtocolBufferException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Create service
    FakeSocket socket = new FakeSocket().withInput("bad response".getBytes());
    SocketRpcChannel rpcChannel = new SocketRpcChannel("host", -1,
        new FakeSocketFactory().returnsSocket(socket));
    SocketRpcController controller = rpcChannel.newRpcController();
    TestService service = TestProtos.TestService.newStub(rpcChannel);

    // Call rpc method
    FakeCallback callback = new FakeCallback();
    service.testMethod(controller, request, callback);

    // Verify request was send and bad response received
    assertEquals(request.toByteString(), socket.getRequest().getRequestProto());
    assertTrue(controller.failed());
    assertEquals(ErrorReason.IOError, controller.errorReason());
    assertFalse(callback.invoked);
  }
}
