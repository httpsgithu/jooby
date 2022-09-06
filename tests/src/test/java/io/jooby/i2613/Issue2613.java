package io.jooby.i2613;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

import com.google.common.collect.ImmutableMap;
import io.jooby.Context;
import io.jooby.MediaType;
import io.jooby.MessageEncoder;
import io.jooby.json.JacksonModule;
import io.jooby.junit.ServerTest;
import io.jooby.junit.ServerTestRunner;

public class Issue2613 {

  public static class ThemeResult {
    public String html() {
      return "<html></html>";
    }
  }

  public static class ThemeResultEncoder implements MessageEncoder {

    @Override public byte[] encode(Context ctx, Object value) throws Exception {
      if (value instanceof ThemeResult) {
        ctx.setDefaultResponseType(MediaType.html);
        return ((ThemeResult) value).html().getBytes(StandardCharsets.UTF_8);
      }
      return null;
    }

  }

  @ServerTest public void shouldConsiderRouteProduces(ServerTestRunner runner) {
    runner.define(app -> {
      app.encoder(MediaType.html, new ThemeResultEncoder());
      app.install(new JacksonModule());

      app.get("/2613/json", ctx -> ImmutableMap.of("foo", "bar"))
          .produces(MediaType.json);
    }).ready(http -> {
      http.get("/2613/json", rsp -> {
        assertEquals("{\"foo\":\"bar\"}", rsp.body().string());
      });
    });
  }
}
