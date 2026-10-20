package com.zgl.zflow.infrastructure.office;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Bounds the entire response (including slow bodies), without an extra worker pool. */
public class BoundedModelHttpRequestFactory implements ClientHttpRequestFactory {
    private final HttpClient client;
    private final Duration timeout;
    private final int maxBytes;
    private byte[] responseBody;
    public byte[] responseBody() { return responseBody; }
    public BoundedModelHttpRequestFactory(HttpClient client,Duration timeout,int maxBytes) {
        this.client=client; this.timeout=timeout; this.maxBytes=maxBytes;
    }
    @Override public ClientHttpRequest createRequest(URI uri,HttpMethod method) {
        return new AbstractClientHttpRequest() {
            private final ByteArrayOutputStream requestBody=new ByteArrayOutputStream();
            public HttpMethod getMethod(){return method;}
            public URI getURI(){return uri;}
            protected OutputStream getBodyInternal(HttpHeaders headers){return requestBody;}
            protected ClientHttpResponse executeInternal(HttpHeaders headers)throws IOException {
                HttpRequest.Builder request=HttpRequest.newBuilder(uri).timeout(timeout)
                        .method(method.name(),HttpRequest.BodyPublishers.ofByteArray(requestBody.toByteArray()));
                headers.forEach((name,values)->{
                    if(!Set.of("content-length","host","connection","expect","upgrade").contains(name.toLowerCase(Locale.ROOT)))
                        values.forEach(value->request.header(name,value));
                });
                CompletableFuture<HttpResponse<byte[]>> pending=client.sendAsync(request.build(), info->new LimitedBody(maxBytes));
                try {
                    HttpResponse<byte[]> response=pending.get(Math.max(1,timeout.toMillis()),TimeUnit.MILLISECONDS);
                    responseBody=response.body();
                    return new ClientHttpResponse() {
                        public HttpStatusCode getStatusCode(){return HttpStatusCode.valueOf(response.statusCode());}
                        public String getStatusText(){return String.valueOf(response.statusCode());}
                        public HttpHeaders getHeaders(){HttpHeaders h=new HttpHeaders();response.headers().map().forEach(h::put);return h;}
                        public InputStream getBody(){return new ByteArrayInputStream(response.body());}
                        public void close(){}
                    };
                } catch(InterruptedException e){pending.cancel(true);Thread.currentThread().interrupt();throw new InterruptedIOException("Model call interrupted");}
                catch(TimeoutException e){pending.cancel(true);throw new java.net.SocketTimeoutException("Model response deadline exceeded");}
                catch(ExecutionException e){pending.cancel(true);throw new IOException("Model transport failed",e.getCause());}
            }
        };
    }
    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        private final int limit;
        private Flow.Subscription subscription;
        LimitedBody(int limit){this.limit=limit;}
        public CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(Flow.Subscription s){subscription=s;s.request(1);}
        public void onNext(List<ByteBuffer> buffers) {
            for(ByteBuffer buffer:buffers) {
                if((long)bytes.size()+buffer.remaining()>limit){subscription.cancel();result.completeExceptionally(new ResponseTooLarge());return;}
                byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable error){result.completeExceptionally(error);}
        public void onComplete(){result.complete(bytes.toByteArray());}
    }
    public static final class ResponseTooLarge extends IOException {}
}
