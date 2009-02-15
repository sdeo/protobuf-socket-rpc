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

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;
import com.googlecode.protobuf.socketrpc.SocketRpcProtos.Response.ServerErrorReason;
import com.googlecode.protobuf.socketrpc.TestProtos.Request;
import com.googlecode.protobuf.socketrpc.TestProtos.Response;
import com.googlecode.protobuf.socketrpc.TestProtos.TestService;

import junit.framework.TestCase;

/**
 * Unit tests for {@link SocketRpcServer}
 *
 * @author Shardul Deo
 */
public class SocketRpcServerTest extends TestCase {

  private final SocketRpcServer socketRpcServer = new SocketRpcServer(-1, null);

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  private static class ServiceImpl extends TestService {

    private String error = null;
    private RuntimeException rex = null;
    private boolean invokeCallback = false;
    private Response response = null;

    private ServiceImpl failsWithError(String error) {
      this.error = error;
      return this;
    }

    private ServiceImpl throwsException(RuntimeException e) {
      this.rex = e;
      return this;
    }

    private ServiceImpl withCallback(Response response) {
      invokeCallback = true;
      this.response = response;
      return this;
    }

    public void testMethod(RpcController controller, Request request,
        RpcCallback<Response> done) {
      if (error != null) {
        controller.setFailed(error);
      }
      if (rex != null) {
        throw rex;
      }
      if (!invokeCallback) {
        return;
      }
      done.run(response);
    }
  }

  /**
   * Test good request/response
   */
  public void testGoodRpc() throws InvalidProtocolBufferException {
    // Create data
    String reqdata = "Request Data";
    String resdata = "Response Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();
    Response response = Response.newBuilder().setStrData(resdata).build();

    // Call handler
    socketRpcServer.registerService(new ServiceImpl().withCallback(response));
    FakeSocket socket = new FakeSocket().withRequest(createRpcRequest(request));
    socketRpcServer.new Handler(socket).run();

    // Verify result
    assertTrue(socket.getResponse().getCallback());
    assertEquals(response.toByteString(), socket.getResponse().getResponseProto());
  }

  /**
   * Successful RPC but callback is not called.
   */
  public void testNoCallback() throws InvalidProtocolBufferException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Call handler
    socketRpcServer.registerService(new ServiceImpl());
    FakeSocket socket = new FakeSocket().withRequest(createRpcRequest(request));
    socketRpcServer.new Handler(socket).run();

    // Verify result
    assertFalse(socket.getResponse().getCallback());
  }

  /**
   * Successful RPC but callback is called with null.
   */
  public void testNullCallBack() throws InvalidProtocolBufferException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Call handler
    socketRpcServer.registerService(new ServiceImpl().withCallback(null));
    FakeSocket socket = new FakeSocket().withRequest(createRpcRequest(request));
    socketRpcServer.new Handler(socket).run();

    // Verify result
    assertTrue(socket.getResponse().getCallback());
    assertFalse(socket.getResponse().hasResponseProto());
  }

  /**
   * Server receives corrupt bytes in request.
   */
  public void testBadRequest() throws InvalidProtocolBufferException {
    // Call handler
    socketRpcServer.registerService(new ServiceImpl());
    FakeSocket socket = new FakeSocket().withInputBytes("bad bytes".getBytes());
    socketRpcServer.new Handler(socket).run();

    // Verify result
    assertFalse(socket.getResponse().getCallback());
    assertTrue(socket.getResponse().hasError());
    assertEquals(ServerErrorReason.BAD_REQUEST_DATA,
        socket.getResponse().getErrorReason());
  }

  /**
   * Server is called with RPC for unknown service.
   */
  public void testInvalidService() throws InvalidProtocolBufferException {
    // Create data
    String reqdata = "Request Data";
    String resdata = "Response Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();
    Response response = Response.newBuilder().setStrData(resdata).build();

    // Call handler
    socketRpcServer.registerService(new ServiceImpl().withCallback(response));
    FakeSocket socket = new FakeSocket().withRequest(createRpcRequest(
        "BadService", "", request.toByteString()));
    socketRpcServer.new Handler(socket).run();

    // Verify result
    assertFalse(socket.getResponse().getCallback());
    assertTrue(socket.getResponse().hasError());
    assertEquals(ServerErrorReason.SERVICE_NOT_FOUND,
        socket.getResponse().getErrorReason());
  }

  /**
   * Server is called with RPC for unknown method.
   */
  public void testInvalidMethod() throws InvalidProtocolBufferException {
    // Create data
    String reqdata = "Request Data";
    String resdata = "Response Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();
    Response response = Response.newBuilder().setStrData(resdata).build();

    // Call handler
    socketRpcServer.registerService(new ServiceImpl().withCallback(response));
    FakeSocket socket = new FakeSocket().withRequest(createRpcRequest(
        TestService.getDescriptor().getFullName(), "BadMethod",
        request.toByteString()));
    socketRpcServer.new Handler(socket).run();

    // Verify result
    assertFalse(socket.getResponse().getCallback());
    assertTrue(socket.getResponse().hasError());
    assertEquals(ServerErrorReason.METHOD_NOT_FOUND,
        socket.getResponse().getErrorReason());
  }

  /**
   * RPC Request proto is invalid.
   */
  public void testInvalidRequestProto() throws InvalidProtocolBufferException {
    // Create data
    String resdata = "Response Data";
    Response response = Response.newBuilder().setStrData(resdata).build();

    // Call handler
    socketRpcServer.registerService(new ServiceImpl().withCallback(response));
    FakeSocket socket = new FakeSocket().withRequest(createRpcRequest(TestService.getDescriptor().getFullName(),
        TestService.getDescriptor().getMethods().get(0).getName(),
        ByteString.copyFrom("Bad Request".getBytes())));
    socketRpcServer.new Handler(socket).run();

    // Verify result
    assertFalse(socket.getResponse().getCallback());
    assertTrue(socket.getResponse().hasError());
    assertEquals(ServerErrorReason.BAD_REQUEST_PROTO,
        socket.getResponse().getErrorReason());
  }

  /**
   * RPC throws exception.
   */
  public void testRPCException() throws InvalidProtocolBufferException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Call handler
    socketRpcServer.registerService(new ServiceImpl()
        .throwsException(new RuntimeException()));
    FakeSocket socket = new FakeSocket().withRequest(createRpcRequest(request));
    socketRpcServer.new Handler(socket).run();

    // Verify result
    assertFalse(socket.getResponse().getCallback());
    assertTrue(socket.getResponse().hasError());
    assertEquals(ServerErrorReason.RPC_ERROR,
        socket.getResponse().getErrorReason());
  }

  /**
   * RPC fails.
   */
  public void testRPCFailed() throws InvalidProtocolBufferException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Call handler
    socketRpcServer.registerService(new ServiceImpl().failsWithError("Error"));
    FakeSocket socket = new FakeSocket().withRequest(createRpcRequest(request));
    socketRpcServer.new Handler(socket).run();

    // Verify result
    assertFalse(socket.getResponse().getCallback());
    assertTrue(socket.getResponse().hasError());
    assertEquals(ServerErrorReason.RPC_FAILED,
        socket.getResponse().getErrorReason());
  }
  private SocketRpcProtos.Request createRpcRequest(Request request) {
    return createRpcRequest(TestService.getDescriptor().getFullName(),
        TestService.getDescriptor().getMethods().get(0).getName(),
        request.toByteString());
  }

  private SocketRpcProtos.Request createRpcRequest(String service,
      String method, ByteString request) {
    return SocketRpcProtos.Request.newBuilder()
        .setServiceName(service)
        .setMethodName(method)
        .setRequestProto(request)
        .build();
  }
}
