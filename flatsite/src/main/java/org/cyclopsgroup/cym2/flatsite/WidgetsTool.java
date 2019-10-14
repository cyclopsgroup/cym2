package org.cyclopsgroup.cym2.flatsite;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;

public class WidgetsTool {
  public static final String TOOL_NAME = "widgets";

  public String renderHtmlBody(String url) throws IOException {
    return renderHtmlBody(new URL(url));
  }

  public String renderHtmlBody(URL url) throws IOException {
    try (InputStream in = url.openStream()) {
      String content = IOUtils.toString(in, StandardCharsets.UTF_8);
      if (content.contains("</body>")) {
        content = content.replaceAll("^.*<body>", "<div>");
        content = content.replaceAll("</body>.*$", "</div>");
      }
      return content;
    }
  }
}
