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
import java.util.concurrent.Executor;

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
 * Tests for {@link RpcChannelImpl}.
 *
 * @author Shardul Deo
 */
public class RpcChannelImplTest extends TestCase {

  private FakeSocket socket;
  private RpcConnectionFactory connectionFactory;
  private RpcChannelImpl rpcChannel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    socket = new FakeSocket(true);
    connectionFactory = new SocketRpcConnectionFactory("host", 8080,
        new FakeSocketFactory().returnsSocket(socket), true /* delimited */);
    rpcChannel = new RpcChannelImpl(connectionFactory,
        RpcChannels.SAME_THREAD_EXECUTOR);
  }

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
    socket.withResponseProto(response);

    // Call non-blocking method
    FakeCallback callback = callRpc(request, null);
    verifyRequestToSocket(request);

    // Verify response
    assertTrue(callback.invoked);
    assertEquals(resdata, callback.response.getStrData());

    // Call blocking method
    assertEquals(resdata, callBlockingRpc(request, null).getStrData());
    verifyRequestToSocket(request);

    // Call method asynchronously
    callback = callAsyncRpc(request, null);
    verifyRequestToSocket(request);

    // Verify response
    assertTrue(callback.invoked);
    assertEquals(resdata, callback.response.getStrData());
  }

  /**
   * Rpc called with incomplete request proto
   */
  public void testIncompleteRequest() throws IOException {
    // Create data
    String resdata = "Response Data";
    Request request = Request.newBuilder().buildPartial(); // required missing
    Response response = Response.newBuilder().setStrData(resdata).build();
    socket.withResponseProto(response);

    // Call non-blocking method
    callRpc(request, ErrorReason.INVALID_REQUEST_PROTO);

    // Call blocking method
    assertNull(callBlockingRpc(request, ErrorReason.INVALID_REQUEST_PROTO));

    // Call method asynchronously
    callAsyncRpc(request, ErrorReason.INVALID_REQUEST_PROTO,
        false /* no listener */);
  }

  /**
   * RPC doesn't invoke callback.
   */
  public void testNoCallBack() throws IOException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();
    socket.withNoResponse(false /* callback */);

    // Call non-blocking method
    FakeCallback callback = callRpc(request, null);
    verifyRequestToSocket(request);

    // Verify callback not called
    assertFalse(callback.invoked);

    // Call blocking method
    assertNull(callBlockingRpc(request, null));
    verifyRequestToSocket(request);

    // Call method asynchronously
    callback = callAsyncRpc(request, null);
    verifyRequestToSocket(request);

    // Verify callback not called
    assertFalse(callback.invoked);
  }

  /**
   * RPC invokes callback with null.
   */
  public void testNullCallBack() throws IOException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();
    socket.withNoResponse(true /* callback */);

    // Call non-blocking method
    FakeCallback callback = callRpc(request, null);
    verifyRequestToSocket(request);

    // Verify callback was called with null
    assertTrue(callback.invoked);
    assertNull(callback.response);

    // Call blocking method
    assertNull(callBlockingRpc(request, null));
    verifyRequestToSocket(request);

    // Call method asynchronously
    callback = callAsyncRpc(request, null);
    verifyRequestToSocket(request);

    // Verify callback was called with null
    assertTrue(callback.invoked);
    assertNull(callback.response);
  }

  /**
   * Server responds with bad data
   */
  public void testBadResponse() throws IOException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();
    socket.withInputBytes("bad response".getBytes());

    // Call non-blocking method
    callRpc(request, ErrorReason.IO_ERROR);
    verifyRequestToSocket(request);

    // Call blocking method
    assertNull(callBlockingRpc(request, ErrorReason.IO_ERROR));
    verifyRequestToSocket(request);

    // Call method asynchronously
    callAsyncRpc(request, ErrorReason.IO_ERROR);
    verifyRequestToSocket(request);
  }

  /**
   * RPC responds with bad response proto
   */
  public void testBadResponseProto() throws IOException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();
    socket.withResponseProto(ByteString.copyFrom("bad response".getBytes()));

    callRpc(request, ErrorReason.BAD_RESPONSE_PROTO);
    verifyRequestToSocket(request);

    // Call blocking method
    assertNull(callBlockingRpc(request, ErrorReason.BAD_RESPONSE_PROTO));
    verifyRequestToSocket(request);

    callAsyncRpc(request, ErrorReason.BAD_RESPONSE_PROTO);
    verifyRequestToSocket(request);
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
    socket.withResponseProto(response);

    callRpc(request, ErrorReason.BAD_RESPONSE_PROTO);
    verifyRequestToSocket(request);

    // Call blocking method
    assertNull(callBlockingRpc(request, ErrorReason.BAD_RESPONSE_PROTO));
    verifyRequestToSocket(request);

    callAsyncRpc(request, ErrorReason.BAD_RESPONSE_PROTO);
    verifyRequestToSocket(request);
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
    socket.withErrorResponseProto(error, reason);

    callRpc(request, reason);
    verifyRequestToSocket(request);

    // Call blocking method
    assertNull(callBlockingRpc(request, reason));
    verifyRequestToSocket(request);

    callAsyncRpc(request, reason);
    verifyRequestToSocket(request);
  }

  private FakeCallback callRpc(Request request, ErrorReason reason) {
    SocketRpcController controller = new SocketRpcController();
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

  /**
   * Executor that just stores commands to be executed later.
   */
  private static class DelayedExecutor implements Executor {

    private Runnable listener = null;

    @Override
    public void execute(Runnable command) {
      listener = command;
    }
  }

  private FakeCallback callAsyncRpc(Request request, ErrorReason reason) {
    return callAsyncRpc(request, reason, true);
  }

  private FakeCallback callAsyncRpc(Request request, ErrorReason reason,
      boolean hasListener) {
    SocketRpcController controller = new SocketRpcController();
    DelayedExecutor executor = new DelayedExecutor();
    TestService service = TestService.newStub(
        new RpcChannelImpl(connectionFactory, executor));
    FakeCallback callback = new FakeCallback();
    service.testMethod(controller, request, callback);

    assertEquals(hasListener, executor.listener != null);
    if (hasListener) {
      // Callback should not be called yet since it is async
      assertFalse(callback.invoked);
      executor.listener.run();
    }

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

  private Response callBlockingRpc(Request request, ErrorReason reason) {
    SocketRpcController controller = new SocketRpcController();
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

  private void verifyRequestToSocket(Request request) throws IOException {
    assertEquals(request.toByteString(), socket.getRequest().getRequestProto());
    assertEquals(TestService.getDescriptor().getFullName(), socket.getRequest()
        .getServiceName());
    assertEquals(TestService.getDescriptor().getMethods().get(0).getName(),
        socket.getRequest().getMethodName());
  }
}
