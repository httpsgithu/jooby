package io.jooby;

import examples.InstanceRouter;
import examples.JAXRS;
import examples.LoopDispatch;
import examples.Message;
import examples.MvcBody;
import examples.MyValueRouter;
import examples.NoTopLevelPath;
import examples.NullInjection;
import examples.ProducesConsumes;
import examples.Provisioning;
import examples.TopDispatch;
import io.jooby.json.JacksonModule;
import io.jooby.junit.ServerTest;
import io.jooby.junit.ServerTestRunner;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;

import javax.annotation.Nonnull;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.Executors;

import static io.jooby.MediaType.xml;
import static okhttp3.RequestBody.create;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MvcTest {

  @ServerTest
  public void routerInstance(ServerTestRunner runner) {
    runner.define(app -> {

      app.mvc(new InstanceRouter());

    }).ready(client -> {
      client.get("/", rsp -> {
        assertEquals("Got it!", rsp.body().string());
      });

      client.post("/", rsp -> {
        assertEquals("Got it!", rsp.body().string());
      });

      client.get("/subpath", rsp -> {
        assertEquals("OK", rsp.body().string());
      });

      client.delete("/void", rsp -> {
        assertEquals("", rsp.body().string());
        assertEquals(204, rsp.code());
      });

      client.get("/voidwriter", rsp -> {
        assertEquals("writer", rsp.body().string().trim());
        assertEquals(200, rsp.code());
      });
    });
  }

  @ServerTest
  public void routerImporting(ServerTestRunner runner) {
    runner.define(app -> {
      Jooby sub = new Jooby();
      sub.mvc(new InstanceRouter());
      app.use("/sub", sub);
    }).ready(client -> {
      client.get("/sub", rsp -> {
        assertEquals("Got it!", rsp.body().string());
      });
      client.post("/sub", rsp -> {
        assertEquals("Got it!", rsp.body().string());
      });

      client.get("/sub/subpath", rsp -> {
        assertEquals("OK", rsp.body().string());
      });

      client.delete("/sub/void", rsp -> {
        assertEquals("", rsp.body().string());
        assertEquals(204, rsp.code());
      });

      client.get("/sub/voidwriter", rsp -> {
        assertEquals("writer", rsp.body().string().trim());
        assertEquals(200, rsp.code());
      });
    });
  }

  @ServerTest
  public void producesAndConsumes(ServerTestRunner runner) {
    runner.define(app -> {

      app.encoder(io.jooby.MediaType.json, (@Nonnull Context ctx, @Nonnull Object value) ->
          ("{" + value.toString() + "}").getBytes(StandardCharsets.UTF_8)
      );

      app.encoder(io.jooby.MediaType.xml, (@Nonnull Context ctx, @Nonnull Object value) ->
          ("<" + value.toString() + ">").getBytes(StandardCharsets.UTF_8)
      );

      app.decoder(io.jooby.MediaType.json, new MessageDecoder() {
        @Nonnull @Override public Message decode(@Nonnull Context ctx, @Nonnull Type type)
            throws Exception {
          return new Message("{" + ctx.body().value("") + "}");
        }
      });

      app.decoder(xml, new MessageDecoder() {
        @Nonnull @Override public Message decode(@Nonnull Context ctx, @Nonnull Type type)
            throws Exception {
          return new Message("<" + ctx.body().value("") + ">");
        }
      });

      app.mvc(new ProducesConsumes());

    }).ready(client -> {
      client.header("Accept", "application/json");
      client.get("/produces", rsp -> {
        assertEquals("{MVC}", rsp.body().string());
      });

      client.header("Accept", "application/xml");
      client.get("/produces", rsp -> {
        assertEquals("<MVC>", rsp.body().string());
      });

      client.header("Accept", "text/html");
      client.get("/produces", rsp -> {
        assertEquals(406, rsp.code());
      });

      client.header("Content-Type", "application/json");
      client.get("/consumes", rsp -> {
        assertEquals("{}", rsp.body().string());
      });

      client.header("Content-Type", "application/xml");
      client.get("/consumes", rsp -> {
        assertEquals("<>", rsp.body().string());
      });

      client.header("Content-Type", "text/plain");
      client.get("/consumes", rsp -> {
        assertEquals(415, rsp.code());
      });

      client.get("/consumes", rsp -> {
        assertEquals(415, rsp.code());
      });
    });
  }

  @ServerTest
  public void jaxrs(ServerTestRunner runner) {
    runner.define(app -> {

      app.mvc(new JAXRS());

    }).ready(client -> {
      client.get("/jaxrs", rsp -> {
        assertEquals("Got it!", rsp.body().string());
      });
    });
  }

  @ServerTest
  public void noTopLevelPath(ServerTestRunner runner) {
    runner.define(app -> {

      app.mvc(new NoTopLevelPath());

    }).ready(client -> {
      client.get("/", rsp -> {
        assertEquals("root", rsp.body().string());
      });

      client.get("/subpath", rsp -> {
        assertEquals("subpath", rsp.body().string());
      });
    });
  }

  @ServerTest
  public void provisioning(ServerTestRunner runner) {
    runner.define(app -> {

      app.mvc(new Provisioning());

    }).ready(client -> {
      client.get("/args/ctx", rsp -> {
        assertEquals("/args/ctx", rsp.body().string());
      });

      client.header("Cookie", "jooby.flash=success=OK").get("/args/flash", rsp -> {
        assertEquals("{success=OK}OK", rsp.body().string());
      });

      client.get("/args/sendStatusCode", rsp -> {
        assertEquals("", rsp.body().string());
        assertEquals(201, rsp.code());
      });

      client.get("/args/file/foo.txt", rsp -> {
        assertEquals("foo.txt", rsp.body().string());
      });

      client.get("/args/int/678", rsp -> {
        assertEquals("678", rsp.body().string());
      });

      client.get("/args/foo/678/9/3.14/6.66/true", rsp -> {
        assertEquals("GET/foo/678/9/3.14/6.66/true", rsp.body().string());
      });

      client.get("/args/long/678", rsp -> {
        assertEquals("678", rsp.body().string());
      });

      client.get("/args/float/67.8", rsp -> {
        assertEquals("67.8", rsp.body().string());
      });

      client.get("/args/double/67.8", rsp -> {
        assertEquals("67.8", rsp.body().string());
      });

      client.get("/args/bool/false", rsp -> {
        assertEquals("false", rsp.body().string());
      });
      client.get("/args/str/*", rsp -> {
        assertEquals("*", rsp.body().string());
      });
      client.get("/args/list/*", rsp -> {
        assertEquals("[*]", rsp.body().string());
      });
      client.get("/args/custom/3.14", rsp -> {
        assertEquals("3.14", rsp.body().string());
      });

      client.get("/args/search?q=*", rsp -> {
        assertEquals("*", rsp.body().string());
      });

      client.get("/args/querystring?q=*", rsp -> {
        assertEquals("{q=*}", rsp.body().string());
      });

      client.get("/args/search-opt", rsp -> {
        assertEquals("Optional.empty", rsp.body().string());
      });

      client.header("foo", "bar");
      client.get("/args/header", rsp -> {
        assertEquals("bar", rsp.body().string());
      });

      client.post("/args/form", new FormBody.Builder().add("foo", "ab").build(), rsp -> {
        assertEquals("ab", rsp.body().string());
      });

      client.post("/args/formdata", new FormBody.Builder().add("foo", "ab").build(), rsp -> {
        assertEquals("{foo=ab}", rsp.body().string());
      });

      client.post("/args/multipart", new FormBody.Builder().add("foo", "ab").build(), rsp -> {
        assertEquals("{foo=ab}", rsp.body().string());
      });

      client.post("/args/multipart",
          new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("foo", "...")
              .build(), rsp -> {
            assertEquals("{foo=...}", rsp.body().string());
          });

      client.post("/args/form",
          new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("foo", "...")
              .build(), rsp -> {
            assertEquals("...", rsp.body().string());
          });

      client.header("Cookie", "foo=bar");
      client.get("/args/cookie", rsp -> {
        assertEquals("bar", rsp.body().string());
      });
    });
  }

  @ServerTest
  public void nullinjection(ServerTestRunner runner) {
    runner.define(app -> {

      app.mvc(new NullInjection());

      app.error((ctx, cause, statusCode) -> {
        app.getLog().error("{} {}", ctx.getMethod(), ctx.getRequestPath(), cause);
        ctx.setResponseCode(statusCode)
            .send(cause.getMessage());
      });

    }).ready(client -> {
      client.get("/nonnull", rsp -> {
        assertEquals("Missing value: 'v'", rsp.body().string());
      });
      client.get("/nullok", rsp -> {
        assertEquals("null", rsp.body().string());
      });

      client.get("/nullbean", rsp -> {
        assertEquals(
            "Unable to provision parameter: 'foo: int', require by: constructor examples.NullInjection.QParam(int, java.lang.Integer)",
            rsp.body().string());
      });

      client.get("/nullbean?foo=foo", rsp -> {
        assertEquals(
            "Unable to provision parameter: 'foo: int', require by: constructor examples.NullInjection.QParam(int, java.lang.Integer)",
            rsp.body().string());
      });

      client.get("/nullbean?foo=0&baz=baz", rsp -> {
        assertEquals(
            "Unable to provision parameter: 'baz: int', require by: method examples.NullInjection.QParam.setBaz(int)",
            rsp.body().string());
      });
    });
  }

  @ServerTest
  public void mvcBody(ServerTestRunner runner) {
    runner.define(app -> {

      app.install(new JacksonModule());

      app.mvc(new MvcBody());

      app.error((ctx, cause, statusCode) -> {
        app.getLog()
            .error("{} {} {}", ctx.getMethod(), ctx.getRequestPath(), statusCode.value(), cause);
        ctx.setResponseCode(statusCode)
            .send(cause.getMessage());
      });

    }).ready(client -> {
      client.header("Content-Type", "application/json");
      client.post("/body/json", create("{\"foo\": \"bar\"}", MediaType.get("application/json")),
          rsp -> {
            assertEquals("{foo=bar}null", rsp.body().string());
          });

      client.header("Content-Type", "text/plain");
      client.post("/body/str", create("...", MediaType.get("text/plain")), rsp -> {
        assertEquals("...", rsp.body().string());
      });
      client.header("Content-Type", "text/plain");
      client.post("/body/int", create("8", MediaType.get("text/plain")), rsp -> {
        assertEquals("8", rsp.body().string());
      });
      client.post("/body/int", create("8x", MediaType.get("text/plain")), rsp -> {
        assertEquals("Cannot convert value: 'body', to: 'int'", rsp.body().string());
      });
      client.header("Content-Type", "application/json");
      client.post("/body/json", create("{\"foo\"= \"bar\"}", MediaType.get("application/json")),
          rsp -> {
            assertEquals(400, rsp.code());
          });

      client.header("Content-Type", "application/json");
      client.post("/body/json?type=x",
          create("{\"foo\": \"bar\"}", MediaType.get("application/json")), rsp -> {
            assertEquals("{foo=bar}x", rsp.body().string());
          });
    });
  }

  @ServerTest
  public void beanConverter(ServerTestRunner runner) {
    runner.define(app -> {
      app.converter(new MyValueBeanConverter());
      app.mvc(new MyValueRouter());
    }).ready(client -> {
      client.get("/myvalue?string=query", rsp -> {
        assertEquals("query", rsp.body().string());
      });
    });
  }

  @ServerTest(executionMode = ExecutionMode.EVENT_LOOP)
  public void mvcDispatch(ServerTestRunner runner) {
    runner.define(app -> {
      app.executor("single", Executors.newSingleThreadExecutor(r ->
          new Thread(r, "single")
      ));

      app.mvc(new TopDispatch());

    }).ready(client -> {
      client.get("/", rsp -> {
        String body = rsp.body().string();
        assertTrue(body.startsWith("worker"), body);
      });

      client.get("/method", rsp -> {
        assertEquals("single", rsp.body().string());
      });
    });
  }

  @ServerTest(executionMode = ExecutionMode.EVENT_LOOP)
  public void mvcLoopDispatch(ServerTestRunner runner) {
    runner.define(app -> {
      app.executor("single", Executors.newSingleThreadExecutor(r ->
          new Thread(r, "single")
      ));

      app.mvc(new LoopDispatch());

    }).ready(client -> {
      client.get("/", rsp -> {
        String body = rsp.body().string();
        assertTrue(runner.matchesEventLoopThread(body), body);
      });

      client.get("/method", rsp -> {
        assertEquals("single", rsp.body().string());
      });
    });
  }

}
