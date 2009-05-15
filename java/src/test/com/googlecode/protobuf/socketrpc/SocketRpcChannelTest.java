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

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.SocketFactory;

import junit.framework.TestCase;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.RpcCallback;
import com.googlecode.protobuf.socketrpc.SocketRpcProtos.ErrorReason;
import com.googlecode.protobuf.socketrpc.TestProtos.Request;
import com.googlecode.protobuf.socketrpc.TestProtos.Response;
import com.googlecode.protobuf.socketrpc.TestProtos.TestService;

/**
 * Unit tests for {@link SocketRpcChannel}
 *
 * @author Shardul Deo
 */
public class SocketRpcChannelTest extends TestCase {

  /**
   * Fake {@link SocketFactory} used for unit tests.
   */
  private static class FakeSocketFactory extends SocketFactory {

    private Socket socket = null;
    private IOException ioException = null;
    private UnknownHostException unknownHostException = null;

    private FakeSocketFactory returnsSocket(Socket socket) {
      this.socket = socket;
      return this;
    }

    private FakeSocketFactory throwsException(IOException e) {
      ioException = e;
      return this;
    }

    private FakeSocketFactory throwsException(UnknownHostException e) {
      unknownHostException = e;
      return this;
    }

    public Socket createSocket() throws IOException {
      if (unknownHostException != null) {
        throw unknownHostException;
      }
      if (ioException != null) {
        throw ioException;
      }
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
   * Error while creating socket
   */
  public void testUnknownHost() {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Create service
    SocketRpcChannel rpcChannel = new SocketRpcChannel("host", -1,
        new FakeSocketFactory().throwsException(new UnknownHostException()));
    SocketRpcController controller = rpcChannel.newRpcController();
    TestService service = TestProtos.TestService.newStub(rpcChannel);

    // Call rpc method
    FakeCallback callback = new FakeCallback();
    service.testMethod(controller, request, callback);

    // Verify error
    assertFalse(callback.invoked);
    assertTrue(controller.failed());
    assertEquals(ErrorReason.UNKNOWN_HOST, controller.errorReason());
  }

  /**
   * Error while creating socket
   */
  public void testIOErrorWhileCreatingSocket() {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Create service
    SocketRpcChannel rpcChannel = new SocketRpcChannel("host", -1,
        new FakeSocketFactory().throwsException(new IOException()));
    SocketRpcController controller = rpcChannel.newRpcController();
    TestService service = TestProtos.TestService.newStub(rpcChannel);

    // Call rpc method
    FakeCallback callback = new FakeCallback();
    service.testMethod(controller, request, callback);

    // Verify error
    assertFalse(callback.invoked);
    assertTrue(controller.failed());
    assertEquals(ErrorReason.IO_ERROR, controller.errorReason());
  }

  /**
   * Rpc called with incomplete request proto
   */
  public void testIncompleteRequest() throws InvalidProtocolBufferException {
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
    assertEquals(ErrorReason.INVALID_REQUEST_PROTO, controller.errorReason());
    assertFalse(callback.invoked);
  }

  /**
   * RPC doesn't invoke callback.
   */
  public void testNoCallBack() throws InvalidProtocolBufferException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Create service
    FakeSocket socket = new FakeSocket().withNoResponse(false);
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
    assertFalse(callback.invoked);
  }

  /**
   * RPC invokes callback with null.
   */
  public void testNullCallBack() throws InvalidProtocolBufferException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Create service
    FakeSocket socket = new FakeSocket().withNoResponse(true);
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
    assertNull(callback.response);
  }

  /**
   * Server responds with bad data
   */
  public void testBadResponse() throws InvalidProtocolBufferException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Create service
    FakeSocket socket = new FakeSocket()
        .withInputBytes("bad response".getBytes());
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
    assertEquals(ErrorReason.IO_ERROR, controller.errorReason());
    assertFalse(callback.invoked);
  }

  /**
   * RPC responds with bad response proto
   */
  public void testBadResponseProto() throws InvalidProtocolBufferException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Create service
    FakeSocket socket = new FakeSocket().withResponseProto(ByteString
        .copyFrom("bad response".getBytes()));
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
    assertEquals(ErrorReason.BAD_RESPONSE_PROTO, controller.errorReason());
    assertFalse(callback.invoked);
  }

  /**
   * RPC responds with incomplete response.
   */
  public void testIncompleteResponse() throws InvalidProtocolBufferException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();
    // incomplete response
    Response response = Response.newBuilder().setIntData(5).buildPartial();

    // Create service
    FakeSocket socket = new FakeSocket().withResponseProto(response);
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
    assertEquals(ErrorReason.BAD_RESPONSE_PROTO, controller.errorReason());
    assertFalse(callback.invoked);
  }

  /**
   * Error on server side.
   */
  public void testErrorResponse() throws InvalidProtocolBufferException {
    checkResponseWithError(ErrorReason.BAD_REQUEST_DATA);
    checkResponseWithError(ErrorReason.BAD_REQUEST_PROTO);
    checkResponseWithError(ErrorReason.SERVICE_NOT_FOUND);
    checkResponseWithError(ErrorReason.METHOD_NOT_FOUND);
    checkResponseWithError(ErrorReason.RPC_ERROR);
    checkResponseWithError(ErrorReason.RPC_FAILED);
  }

  private void checkResponseWithError(ErrorReason reason)
      throws InvalidProtocolBufferException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();
    String error = "Error String";

    // Create service
    FakeSocket socket = new FakeSocket().withErrorResponseProto(error, reason);
    SocketRpcChannel rpcChannel = new SocketRpcChannel("host", -1,
        new FakeSocketFactory().returnsSocket(socket));
    SocketRpcController controller = rpcChannel.newRpcController();
    TestService service = TestProtos.TestService.newStub(rpcChannel);

    // Call rpc method
    FakeCallback callback = new FakeCallback();
    service.testMethod(controller, request, callback);

    // Verify request was send and error response received
    assertEquals(request.toByteString(), socket.getRequest().getRequestProto());
    assertTrue(controller.failed());
    assertEquals(reason, controller.errorReason());
    assertEquals(error, controller.errorText());
    assertFalse(callback.invoked);
  }
}
