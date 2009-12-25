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

import junit.framework.TestCase;

import com.google.protobuf.ByteString;
import com.googlecode.protobuf.socketrpc.RpcForwarder.RpcException;
import com.googlecode.protobuf.socketrpc.SocketRpcProtos.ErrorReason;
import com.googlecode.protobuf.socketrpc.TestProtos.Request;
import com.googlecode.protobuf.socketrpc.TestProtos.Response;
import com.googlecode.protobuf.socketrpc.TestProtos.TestService;

/**
 * Unit tests for {@link RpcForwarder}.
 *
 * @author Shardul Deo
 */
public class RpcForwarderTest extends TestCase {

  private RpcForwarder rpcForwarder;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    rpcForwarder = new RpcForwarder();
  }

  /**
   * Test good request/response
   */
  public void testGoodRpc() throws RpcException {
    // Create data
    String reqdata = "Request Data";
    String resdata = "Response Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();
    Response response = Response.newBuilder().setStrData(resdata).build();

    // Call forwarder
    rpcForwarder.registerService(new FakeServiceImpl().withResponse(response));
    SocketRpcProtos.Response rpcResponse =
        rpcForwarder.doRPC(createRpcRequest(request));

    // Verify result
    assertTrue(rpcResponse.getCallback());
    assertEquals(response.toByteString(), rpcResponse.getResponseProto());

    // Try with blocking service
    response = Response.newBuilder().setStrData("New Data").build();
    rpcForwarder.registerBlockingService(
        new FakeServiceImpl().withResponse(response).toBlockingService());
    rpcResponse = rpcForwarder.doRPC(createRpcRequest(request));

    // Verify result
    assertTrue(rpcResponse.getCallback());
    assertEquals(response.toByteString(), rpcResponse.getResponseProto());
  }

  /**
   * Successful RPC but callback is not called.
   */
  public void testNoCallback() throws RpcException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Call forwarder
    rpcForwarder.registerService(new FakeServiceImpl());
    SocketRpcProtos.Response rpcResponse =
        rpcForwarder.doRPC(createRpcRequest(request));

    // Verify result
    assertFalse(rpcResponse.getCallback());
    assertFalse(rpcResponse.hasResponseProto());

    // Try with blocking service
    rpcForwarder.registerBlockingService(
        new FakeServiceImpl().toBlockingService());
    rpcResponse = rpcForwarder.doRPC(createRpcRequest(request));

    // Verify result
    assertTrue(rpcResponse.getCallback());
    assertFalse(rpcResponse.hasResponseProto());
  }

  /**
   * Successful RPC but callback is called with null.
   */
  public void testNullCallBack() throws RpcException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Call forwarder
    rpcForwarder.registerService(new FakeServiceImpl().withResponse(null));
    SocketRpcProtos.Response rpcResponse =
        rpcForwarder.doRPC(createRpcRequest(request));

    // Verify result
    assertTrue(rpcResponse.getCallback());
    assertFalse(rpcResponse.hasResponseProto());

    // Try with blocking service
    rpcForwarder.registerBlockingService(
        new FakeServiceImpl().withResponse(null).toBlockingService());
    rpcResponse = rpcForwarder.doRPC(createRpcRequest(request));

    // Verify result
    assertTrue(rpcResponse.getCallback());
    assertFalse(rpcResponse.hasResponseProto());
  }

  /**
   * Server is called with RPC for unknown service.
   */
  public void testInvalidService() {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Call forwarder
    rpcForwarder.registerService(new FakeServiceImpl());
    rpcForwarder.registerBlockingService(
        new FakeServiceImpl().toBlockingService());
    try {
      rpcForwarder.doRPC(
          createRpcRequest("BadService", "", request.toByteString()));
      fail("Should have failed");
    } catch (RpcException e) {
      assertEquals(ErrorReason.SERVICE_NOT_FOUND, e.errorReason);
    }
  }

  /**
   * Server is called with RPC for unknown method.
   */
  public void testInvalidMethod() {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Call forwarder
    rpcForwarder.registerService(new FakeServiceImpl());
    assertInvalidMethodFails(request);
    rpcForwarder.registerBlockingService(
        new FakeServiceImpl().toBlockingService());
    assertInvalidMethodFails(request);
  }

  private void assertInvalidMethodFails(Request request) {
    try {
      rpcForwarder.doRPC(createRpcRequest(
          TestService.getDescriptor().getFullName(),
          "BadMethod",
          request.toByteString()));
      fail("Should have failed");
    } catch (RpcException e) {
      assertEquals(ErrorReason.METHOD_NOT_FOUND, e.errorReason);
    }
  }

  /**
   * RPC Request proto is invalid.
   */
  public void testInvalidRequestProto() {
    // Call forwarder
    rpcForwarder.registerService(new FakeServiceImpl());
    assertBadRequestProtoFails();
    rpcForwarder.registerBlockingService(
        new FakeServiceImpl().toBlockingService());
    assertBadRequestProtoFails();
  }

  private void assertBadRequestProtoFails() {
    try {
      rpcForwarder.doRPC(createRpcRequest(
          TestService.getDescriptor().getFullName(),
          TestService.getDescriptor().getMethods().get(0).getName(),
          ByteString.copyFrom("Bad Request".getBytes())));
      fail("Should have failed");
    } catch (RpcException e) {
      assertEquals(ErrorReason.BAD_REQUEST_PROTO, e.errorReason);
    }
  }

  /**
   * RPC throws exception.
   */
  public void testRpcException() {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Call forwarder
    RuntimeException error = new RuntimeException();
    rpcForwarder.registerService(
        new FakeServiceImpl().throwsException(error));
    assertRpcErrorFails(request, error);
    error = new RuntimeException();
    rpcForwarder.registerBlockingService(
        new FakeServiceImpl().throwsException(error).toBlockingService());
    assertRpcErrorFails(request, error);
  }

  private void assertRpcErrorFails(Request request, RuntimeException error) {
    try {
      rpcForwarder.doRPC(createRpcRequest(request));
      fail("Should have failed");
    } catch (RpcException e) {
      assertEquals(ErrorReason.RPC_ERROR, e.errorReason);
      assertSame(error, e.getCause());
    }
  }

  /**
   * RPC fails.
   */
  public void testRPCFailed() throws RpcException {
    // Create data
    String reqdata = "Request Data";
    Request request = Request.newBuilder().setStrData(reqdata).build();

    // Call forwarder
    rpcForwarder.registerService(new FakeServiceImpl().failsWithError("Error"));
    SocketRpcProtos.Response rpcResponse =
        rpcForwarder.doRPC(createRpcRequest(request));

    // Verify result
    assertFalse(rpcResponse.getCallback());
    assertEquals("Error", rpcResponse.getError());
    assertEquals(ErrorReason.RPC_FAILED, rpcResponse.getErrorReason());

    // Call forwarder
    rpcForwarder.registerBlockingService(
        new FakeServiceImpl().failsWithError("New Error").toBlockingService());
    try {
      rpcForwarder.doRPC(createRpcRequest(request));
    } catch (RpcException e) {
      assertEquals(ErrorReason.RPC_FAILED, e.errorReason);
      assertEquals("New Error", e.getMessage());
    }
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
