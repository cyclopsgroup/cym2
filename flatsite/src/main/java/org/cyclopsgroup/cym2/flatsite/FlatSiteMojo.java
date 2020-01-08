package org.cyclopsgroup.cym2.flatsite;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.text.DateFormat;
import java.util.Date;
import org.apache.commons.collections.ExtendedProperties;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.cyclopsgroup.caff.dp.DocumentProcessor;
import org.cyclopsgroup.caff.dp.wiki.WikiDocumentProcessor;

@Mojo(name = "flatsite")
public class FlatSiteMojo extends AbstractMojo {
  private static final String KEY_WIKI_DISABLED = "wikiSyntaxDisabled";

  @Parameter(defaultValue = "${basedir}/target/site")
  private File outputDirectory;

  @Parameter(defaultValue = "${basedir}/src/flatsite")
  private File sourceDirectory;

  @Parameter(defaultValue = "default_layout.vm")
  private String layout;

  @Parameter(defaultValue = ".vm")
  private String templateSuffix;

  @Parameter(defaultValue = "false")
  private boolean wikiEnabled;

  @Parameter(defaultValue = "layout")
  private String layoutKey;

  @Parameter(defaultValue = "body")
  private String bodyKey;

  private final DocumentProcessor wikiParser = new WikiDocumentProcessor();

  private void copyDirectory(File fromDirectory, File toDirectory) throws IOException {
    if (!fromDirectory.isDirectory()) {
      return;
    }
    if (!toDirectory.isDirectory()) {
      getLog().info("Making directory " + toDirectory);
      toDirectory.mkdirs();
    }
    for (File file : fromDirectory.listFiles()) {
      if (file.getName().charAt(0) == '.') {
        continue;
      } else if (file.isDirectory()) {
        copyDirectory(file, new File(toDirectory, file.getName()));
      } else {
        getLog().info("Copy file from " + file + " into " + toDirectory);
        FileUtils.copyFileToDirectory(file, toDirectory);
      }
    }
  }

  /** @inheritDoc */
  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (!sourceDirectory.isDirectory()) {
      getLog().info(sourceDirectory + " doesn't exist. There's nothing to do");
      return;
    }
    if (!outputDirectory.isDirectory()) {
      getLog().info("Makding destination directory " + outputDirectory);
      outputDirectory.mkdirs();
    }

    try {
      ExtendedProperties props = new ExtendedProperties();
      props.load(getClass().getClassLoader().getResourceAsStream("flatsite-velocity.properties"));
      getLog().info(props.getString("file.resource.loader.path"));
      VelocityEngine velocityEngine = new VelocityEngine();
      velocityEngine.setExtendedProperties(props);
      velocityEngine.setProperty("file.resource.loader.path", sourceDirectory.getAbsolutePath());
      velocityEngine.init();
      generateSiteDirectory("", velocityEngine);
      copyDirectory(new File(sourceDirectory, "resources"), outputDirectory);
    } catch (Exception e) {
      getLog().error("Velocity error", e);
    }
  }

  private void generateSiteDirectory(String relativeDirectory, VelocityEngine velocityEngine)
      throws IOException {
    File currentSourceDirectory = new File(sourceDirectory, "content/" + relativeDirectory);
    File[] files = currentSourceDirectory.listFiles();
    for (File file : files) {
      if (file.getName().charAt(0) == '.') {
        continue;
      }
      if (file.isDirectory()) {
        generateSiteDirectory(mergePath(relativeDirectory, file.getName()), velocityEngine);
      } else if (file.getName().endsWith(templateSuffix)) {
        generateSiteFile(relativeDirectory, file.getName(), velocityEngine);
      } else {
        getLog().warn("Ignore resource " + file + " since it's not a velicity template");
      }
    }
  }

  private void generateSiteFile(
      String fileDirectory, String fileName, VelocityEngine velocityEngine) {
    String templatePath = "content/" + mergePath(fileDirectory, fileName);
    File destDirectory = new File(outputDirectory, fileDirectory);
    if (!destDirectory.isDirectory()) {
      getLog().info("Making directory " + destDirectory);
      destDirectory.mkdirs();
    }
    String htmlFileName =
        fileName.substring(0, fileName.length() - templateSuffix.length()) + ".html";
    File htmlFile = new File(destDirectory, htmlFileName);
    Context context = new VelocityContext();
    context.put(layoutKey, layout);
    context.put("templatePath", templatePath);
    context.put("htmlPath", mergePath(fileDirectory, htmlFileName));
    context.put(
        XmlTool.TOOL_NAME, new XmlTool(new File(sourceDirectory.getAbsolutePath() + "/resources")));
    context.put(WidgetsTool.TOOL_NAME, new WidgetsTool());
    context.put("now", new Date());
    context.put("dateFormat", DateFormat.getDateInstance());
    context.put("timeFormat", DateFormat.getTimeInstance());
    context.put("sourceDirectory", sourceDirectory.getAbsolutePath());
    context.put(KEY_WIKI_DISABLED, Boolean.FALSE);

    String basedir;
    if (StringUtils.isEmpty(fileDirectory)) {
      basedir = ".";
    } else if (fileDirectory.indexOf('/') == -1) {
      basedir = "..";
    } else {
      int levels = StringUtils.countMatches(fileDirectory, "/");
      basedir = ".." + StringUtils.repeat("/..", levels);
    }
    context.put("basedir", basedir);
    context.put(PageLinkTool.TOOL_NAME, new PageLinkTool(basedir));
    try {
      StringWriter out = new StringWriter();
      mergeTemplate(templatePath, context, out, velocityEngine);
      if (wikiEnabled && !(Boolean) context.get(KEY_WIKI_DISABLED)) {
        StringReader in = new StringReader(out.toString());
        out = new StringWriter();
        wikiParser.process(in, out);
      }

      String layoutTemplatePath = "layout/" + (String) context.get("layout");
      String body = out.toString();
      context.put(bodyKey, body);

      getLog()
          .info(
              "Generating "
                  + htmlFile
                  + " from template "
                  + templatePath
                  + " with layout "
                  + layoutTemplatePath);
      FileWriter output = new FileWriter(htmlFile);
      mergeTemplate(layoutTemplatePath, context, output, velocityEngine);
      output.flush();
      output.close();
    }
    // Exception doesn't stop the transformation process
    catch (Throwable e) {
      getLog().warn("Generating html file " + htmlFile + " failed! " + e.getMessage(), e);
    }
  }

  private void mergeTemplate(String path, Context context, Writer output, VelocityEngine engine) {
    try {
      engine.mergeTemplate(path, context, output);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(
          "Can't merge velocity template " + path + ". " + e.getMessage(), e);
    }
  }

  private String mergePath(String relativePath, String fileName) {
    return StringUtils.isEmpty(relativePath) ? fileName : relativePath + "/" + fileName;
  }
}
