package org.cyclopsgroup.cym2.uberjar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

/**
 * @description Create executable uber jar
 * @author <a href="mailto:jiaqi.guo@gmail.com>jiaqi</a>
 * @goal uberjar
 * @requiresDependencyResolution runtime
 */
public class UberjarMojo extends AbstractMojo {

  /**
   * @parameter expression="${basedir}/target/${project.artifactId}-${project.version}.jar"
   * @required
   */
  private File artifactJarFile;

  /**
   * Name of main class invoked by classworlds
   *
   * @parameter
   * @required
   * @description Name of the executable main class
   */
  private String mainClass;

  /**
   * @parameter expression="${plugin.artifactMap}"
   * @readonly
   * @required
   */
  private Map<String, Artifact> pluginArtifactMap;

  /**
   * @parameter expression="${project}"
   * @readonly
   * @required
   */
  private MavenProject project;

  /**
   * Name of generated uberjar file
   *
   * @parameter expression="${basedir}/target/${project.artifactId}-uber-${project.version}.jar"
   * @required
   */
  private File uberjarFile;

  private void addAllEntries(JarOutputStream output, File jarFile) throws IOException {
    getLog().info("Adding everything in " + jarFile + " into uberjar...");
    try (JarFile file = new JarFile(jarFile)) {
      for (Enumeration<JarEntry> en = file.entries(); en.hasMoreElements();) {
        JarEntry entry = en.nextElement();
        if (entry.isDirectory()) {
          continue;
        }
        if (entry.getName().toLowerCase().equals("meta-inf/manifest.mf")) {
          continue;
        }
        output.putNextEntry(new JarEntry(entry));
        getLog().debug("Adding entry " + entry.getName() + "(" + entry.getSize() + " bytes)");
        try (InputStream entryInput = file.getInputStream(entry)) {
          IOUtils.copy(entryInput, output);
          output.flush();
        }
      }
    }
  }

  private void addFileEntry(JarOutputStream output, File file, String name) throws IOException {
    if (!file.isFile()) {
      getLog().warn("File " + file + " does not exist, skip it");
      return;
    }

    JarEntry entry = new JarEntry(name);
    entry.setSize(file.length());
    entry.setTime(file.lastModified());
    output.putNextEntry(entry);
    try (FileInputStream input = new FileInputStream(file)) {
      getLog().info("Adding " + entry.getName());
      IOUtils.copy(input, output);
      output.flush();
    }
  }

  /**
   * @inheritDoc
   */
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (!project.getPackaging().equals("jar")) {
      getLog().error(
          "Uberjar is only for jar packaging project! Your project is " + project.getPackaging());
      return;
    }

    if (!artifactJarFile.isFile()) {
      getLog().error(artifactJarFile + " does not exist, please run mvn package first");
      return;
    }

    if (uberjarFile.isFile()) {
      getLog().info("Deleting existing " + uberjarFile);
      uberjarFile.delete();
    }
    if (!uberjarFile.getParentFile().isDirectory()) {
      uberjarFile.getParentFile().mkdirs();
    }

    // Create string writer for classworlds.conf
    StringWriter classworldsConfigContent = new StringWriter();
    PrintWriter classworldsConfig = new PrintWriter(classworldsConfigContent);
    classworldsConfig.println("main is " + mainClass + " from app");
    classworldsConfig.println("[app]");

    try {
      FileOutputStream fileOutput = new FileOutputStream(uberjarFile);
      JarOutputStream output = new JarOutputStream(fileOutput);
      for (Object classpathElement : project.getRuntimeClasspathElements()) {
        File dependency = new File((String) classpathElement);
        if (dependency.isFile()) {
          addFileEntry(output, dependency, "WORLDS-INF/lib/" + dependency.getName());
          classworldsConfig.println("  load ${classworlds.lib}/" + dependency.getName());
        } else {
          getLog().info("Ignore " + dependency + " since it's not a file");
        }
      }

      // Add classworlds.conf into uberjar
      addFileEntry(output, artifactJarFile, "WORLDS-INF/lib/" + artifactJarFile.getName());
      classworldsConfig.println("  load ${classworlds.lib}/" + artifactJarFile.getName());
      getLog().info("Adding " + "WORLDS-INF/conf/classworlds.conf");
      JarEntry configEntry = new JarEntry("WORLDS-INF/conf/classworlds.conf");
      configEntry.setSize(classworldsConfig.toString().length());
      configEntry.setTime(System.currentTimeMillis());
      output.putNextEntry(configEntry);
      classworldsConfig.flush();
      output.write(classworldsConfigContent.toString().getBytes());

      // Add classworlds.jar into uberjar
      Artifact classworldsArtifact = pluginArtifactMap.get("classworlds:classworlds");
      addFileEntry(output, classworldsArtifact.getFile(), "WORLDS-INF/classworlds.jar");

      // Copy everything from classworlds-boot.jar into uberjar
      Artifact classworldsBootArtifact = pluginArtifactMap.get("classworlds:classworlds-boot");
      addAllEntries(output, classworldsBootArtifact.getFile());

      // Add manifest file into uberjar
      JarEntry manifestEntry = new JarEntry("META-INF/MANIFEST.MF");
      manifestEntry.setTime(System.currentTimeMillis());
      output.putNextEntry(manifestEntry);
      getLog().info("Adding Manifest file...");
      IOUtils.copy(getClass().getClassLoader().getResourceAsStream("classworlds-manifest.txt"),
          output);

      output.flush();
      output.close();
    } catch (IOException e) {
      throw new MojoExecutionException("Execution exception", e);
    } catch (DependencyResolutionRequiredException e) {
      throw new MojoExecutionException("Dependencies are not resolved, code problem", e);
    }
  }
}
