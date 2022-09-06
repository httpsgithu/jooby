package io.jooby.i2477;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.jooby.junit.ServerTest;
import io.jooby.junit.ServerTestRunner;

public class Issue2477 {
  @ServerTest
  public void shouldNotGet200WhenFilterFailPostControllerExecution(ServerTestRunner runner) {
    runner.define(app -> {
      app.decorator(next -> ctx -> {
        Object value = next.apply(ctx);
        if (ctx.query("failure").booleanValue(true)) {
          throw new IllegalStateException("Intentional error");
        }
        return value;
      });
      app.mvc(new Controller2477());
    }).ready(http -> {
      http.put("/2477", rsp -> {
        assertEquals(500, rsp.code());
      });
      http.post("/2477", rsp -> {
        assertEquals(500, rsp.code());
      });
    });
  }
}
