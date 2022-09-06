/**
 * Jooby https://jooby.io
 * Apache License Version 2.0 https://jooby.io/LICENSE.txt
 * Copyright 2014 Edgar Espina
 */
package io.jooby.cli;

import picocli.CommandLine;

import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Jooby version. Fetch latest version from maven repository or fallback to package
 * implementation version.
 *
 */
public class Version implements CommandLine.IVersionProvider {

  /** VERSION. */
  public static final String VERSION = doVersion();

  @Override public String[] getVersion() {
    return new String[]{VERSION};
  }

  private static String doVersion() {
    try {
      URL url = URI
          .create("https://search.maven.org/solrsearch/select?q=+g:io.jooby+a:jooby&start=0&rows=1")
          .toURL();
      URLConnection connection = url.openConnection();
      try (Reader in = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
        Map json = Cli.gson.fromJson(in, Map.class);
        Map response = (Map) json.get("response");
        List docs = (List) response.get("docs");
        Map jooby = (Map) docs.get(0);
        return (String) jooby.get("latestVersion");
      }
    } catch (Exception x) {
      return Optional.ofNullable(Version.class.getPackage())
          .map(Package::getImplementationVersion)
          .filter(Objects::nonNull)
          .orElse("2.15.0");
    }
  }
}
