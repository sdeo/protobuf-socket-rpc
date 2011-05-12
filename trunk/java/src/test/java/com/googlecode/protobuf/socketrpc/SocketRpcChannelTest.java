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
import java.net.UnknownHostException;

import junit.framework.TestCase;

import com.google.protobuf.ByteString;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.ServiceException;
import com.googlecode.protobuf.socketrpc.SocketRpcProtos.ErrorReason;
import com.googlecode.protobuf.socketrpc.TestProtos.Request;
import com.googlecode.protobuf.socketrpc.TestProtos.Response;
import com.googlecode.protobuf.socketrpc.TestProtos.TestService;
import com.googlecode.protobuf.socketrpc.TestProtos.TestService.BlockingInterface;

/**
 * Unit tests for {@link SocketRpcChannel}
 *
 * @author Shardul Deo
 */
public class SocketRpcChannelTest extends TestCase {

  private static class FakeCallback implements RpcCallback<Response> {

    private Response response;
    private boolean invoked = false;

    @Override
    public void run(Response response) {
      this.response = response;
      invoked = true;
    }
  };

  public void testGoodRpc() throws IOException {
    // Create data
    String reqdata = "Request Data";
    String resdata = "Response Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();
    Response response = Response.newBuilder().setStrData(resdata).build();

    // Create channel
    FakeSocket socket = new FakeSocket(false).withResponseProto(response);
    SocketRpcChannel rpcChannel = new SocketRpcChannel("host", -1,
        new FakeSocketFactory().returnsSocket(socket));

    // Call async method
    FakeCallback callback = callAsync(rpcChannel, request, null);
    verifyRequestToSocket(request, socket);

    // Verify response
    assertTrue(callback.invoked);
    assertEquals(resdata, callback.response.getStrData());

    // Call sync method
    assertEquals(resdata, callSync(rpcChannel, request, null).getStrData());
    verifyRequestToSocket(request, socket);
  }

  /**
   * Error while creating socket
   */
  public void testUnknownHost() {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Create channel
    SocketRpcChannel rpcChannel = new SocketRpcChannel("host", -1,
        new FakeSocketFactory().throwsException(new UnknownHostException()));

    // Call async method
    callAsync(rpcChannel, request, ErrorReason.UNKNOWN_HOST);

    // Call sync method
    assertNull(callSync(rpcChannel, request, ErrorReason.UNKNOWN_HOST));
  }

  /**
   * Error while creating socket
   */
  public void testIOErrorWhileCreatingSocket() {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Create channel
    SocketRpcChannel rpcChannel = new SocketRpcChannel("host", -1,
        new FakeSocketFactory().throwsException(new IOException()));

    // Call async method
    callAsync(rpcChannel, request, ErrorReason.IO_ERROR);

    // Call sync method
    assertNull(callSync(rpcChannel, request, ErrorReason.IO_ERROR));
  }

  /**
   * Rpc called with incomplete request proto
   */
  public void testIncompleteRequest() throws IOException {
    // Create data
    String resdata = "Response Data";
    Request request = Request.newBuilder().buildPartial(); // required missing
    Response response = Response.newBuilder().setStrData(resdata).build();

    // Create channel
    FakeSocket socket = new FakeSocket(false).withResponseProto(response);
    SocketRpcChannel rpcChannel = new SocketRpcChannel("host", -1,
        new FakeSocketFactory().returnsSocket(socket));

    // Call async method
    callAsync(rpcChannel, request, ErrorReason.INVALID_REQUEST_PROTO);

    // Call sync method
    assertNull(callSync(rpcChannel, request,
        ErrorReason.INVALID_REQUEST_PROTO));
  }

  /**
   * RPC doesn't invoke callback.
   */
  public void testNoCallBack() throws IOException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Create channel
    FakeSocket socket = new FakeSocket(false).withNoResponse(false);
    SocketRpcChannel rpcChannel = new SocketRpcChannel("host", -1,
        new FakeSocketFactory().returnsSocket(socket));

    // Call async method
    FakeCallback callback = callAsync(rpcChannel, request, null);
    verifyRequestToSocket(request, socket);

    // Verify callback not called
    assertFalse(callback.invoked);

    // Call sync method
    assertNull(callSync(rpcChannel, request, null));
    verifyRequestToSocket(request, socket);
  }

  /**
   * RPC invokes callback with null.
   */
  public void testNullCallBack() throws IOException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Create channel
    FakeSocket socket = new FakeSocket(false).withNoResponse(true);
    SocketRpcChannel rpcChannel = new SocketRpcChannel("host", -1,
        new FakeSocketFactory().returnsSocket(socket));

    // Call async method
    FakeCallback callback = callAsync(rpcChannel, request, null);
    verifyRequestToSocket(request, socket);

    // Verify callback was called with null
    assertTrue(callback.invoked);
    assertNull(callback.response);

    // Call sync method
    assertNull(callSync(rpcChannel, request, null));
    verifyRequestToSocket(request, socket);
  }

  /**
   * Server responds with bad data
   */
  public void testBadResponse() throws IOException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Create channel
    FakeSocket socket = new FakeSocket(false)
        .withInputBytes("bad response".getBytes());
    SocketRpcChannel rpcChannel = new SocketRpcChannel("host", -1,
        new FakeSocketFactory().returnsSocket(socket));

    // Call async method
    callAsync(rpcChannel, request, ErrorReason.IO_ERROR);
    verifyRequestToSocket(request, socket);

    // Call sync method
    assertNull(callSync(rpcChannel, request, ErrorReason.IO_ERROR));
    verifyRequestToSocket(request, socket);
  }

  /**
   * RPC responds with bad response proto
   */
  public void testBadResponseProto() throws IOException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Create channel
    FakeSocket socket = new FakeSocket(false).withResponseProto(ByteString
        .copyFrom("bad response".getBytes()));
    SocketRpcChannel rpcChannel = new SocketRpcChannel("host", -1,
        new FakeSocketFactory().returnsSocket(socket));

    // Call async method
    callAsync(rpcChannel, request, ErrorReason.BAD_RESPONSE_PROTO);
    verifyRequestToSocket(request, socket);

    // Call sync method
    assertNull(callSync(rpcChannel, request, ErrorReason.BAD_RESPONSE_PROTO));
    verifyRequestToSocket(request, socket);
  }

  /**
   * RPC responds with incomplete response.
   */
  public void testIncompleteResponse() throws IOException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();
    // incomplete response
    Response response = Response.newBuilder().setIntData(5).buildPartial();

    // Create channel
    FakeSocket socket = new FakeSocket(false).withResponseProto(response);
    SocketRpcChannel rpcChannel = new SocketRpcChannel("host", -1,
        new FakeSocketFactory().returnsSocket(socket));

    // Call async method
    callAsync(rpcChannel, request, ErrorReason.BAD_RESPONSE_PROTO);
    verifyRequestToSocket(request, socket);

    // Call sync method
    assertNull(callSync(rpcChannel, request, ErrorReason.BAD_RESPONSE_PROTO));
    verifyRequestToSocket(request, socket);
  }

  /**
   * Error on server side.
   */
  public void testErrorResponse() throws IOException {
    checkResponseWithError(ErrorReason.BAD_REQUEST_DATA);
    checkResponseWithError(ErrorReason.BAD_REQUEST_PROTO);
    checkResponseWithError(ErrorReason.SERVICE_NOT_FOUND);
    checkResponseWithError(ErrorReason.METHOD_NOT_FOUND);
    checkResponseWithError(ErrorReason.RPC_ERROR);
    checkResponseWithError(ErrorReason.RPC_FAILED);
  }

  private void checkResponseWithError(ErrorReason reason) throws IOException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();
    String error = "Error String";

    // Create channel
    FakeSocket socket = new FakeSocket(false).withErrorResponseProto(error,
        reason);
    SocketRpcChannel rpcChannel = new SocketRpcChannel("host", -1,
        new FakeSocketFactory().returnsSocket(socket));

    // Call async method
    callAsync(rpcChannel, request, reason);
    verifyRequestToSocket(request, socket);

    // Call sync method
    assertNull(callSync(rpcChannel, request, reason));
    verifyRequestToSocket(request, socket);
  }

  private FakeCallback callAsync(SocketRpcChannel rpcChannel,
      Request request, ErrorReason reason) {
    SocketRpcController controller = rpcChannel.newRpcController();
    TestService service = TestService.newStub(rpcChannel);
    FakeCallback callback = new FakeCallback();
    service.testMethod(controller, request, callback);
    if (reason != null) {
      assertTrue(controller.failed());
      assertEquals(reason, controller.errorReason());
      assertTrue(callback.invoked);
      assertNull(callback.response);
    } else {
      assertFalse(controller.failed());
    }
    return callback;
  }

  private Response callSync(SocketRpcChannel rpcChannel,
      Request request, ErrorReason reason) {
    SocketRpcController controller = rpcChannel.newRpcController();
    BlockingInterface service = TestService.newBlockingStub(rpcChannel);
    try {
      Response response = service.testMethod(controller, request);
      assertNull(reason);
      return response;
    } catch (ServiceException e) {
      assertEquals(reason, controller.errorReason());
      return null;
    } finally {
      assertEquals(reason != null, controller.failed());
    }
  }

  private void verifyRequestToSocket(Request request, FakeSocket socket)
      throws IOException {
    assertEquals(request.toByteString(), socket.getRequest().getRequestProto());
    assertEquals(TestService.getDescriptor().getFullName(), socket.getRequest()
        .getServiceName());
    assertEquals(TestService.getDescriptor().getMethods().get(0).getName(),
        socket.getRequest().getMethodName());
  }
}
