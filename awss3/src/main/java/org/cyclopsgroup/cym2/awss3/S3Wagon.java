package org.cyclopsgroup.cym2.awss3;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.activation.MimetypesFileTypeMap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.InputData;
import org.apache.maven.wagon.OutputData;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.StreamWagon;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.resource.Resource;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

/**
 * Amazon S3 wagon provider implementation.
 */
public class S3Wagon extends StreamWagon {
  private static Map<String, String> loadMimeTypes() throws IOException {
    Map<String, String> map = new HashMap<String, String>();
    try (LineNumberReader reader = new LineNumberReader(
        new InputStreamReader(S3Wagon.class.getClassLoader().getResourceAsStream("mime.types")))) {

      for (String line = reader.readLine(); line != null; line = reader.readLine()) {
        if (StringUtils.isBlank(line) || line.startsWith("#")) {
          continue;
        }
        line = line.trim();
        String[] pieces = line.split("\\s+");
        if (pieces.length <= 1) {
          continue;
        }
        String mimeType = pieces[0];
        for (int i = 1; i < pieces.length; i++) {
          map.put(pieces[i], mimeType);
        }
      }
      return map;
    }
  }

  private String bucketName;

  private String keyPrefix;

  private final Map<String, String> mimeTypes;

  private AmazonS3 s3;

  private final MimetypesFileTypeMap typeMap = new MimetypesFileTypeMap();

  /**
   * Default constructor reads mime type mapping from generated properties file for later use
   *
   * @throws IOException Allows IO errors
   */
  public S3Wagon() throws IOException {
    this.mimeTypes = Collections.unmodifiableMap(loadMimeTypes());
  }

  @Override
  public void closeConnection() throws ConnectionException {}

  private void doPutFromStream(InputStream in, File inFile, String destination, long contentLength,
      long lastModified) {
    Resource resource = new Resource(destination);
    firePutInitiated(resource, inFile);

    String dest = StringUtils.removeStart(destination, "./");
    dest = StringUtils.removeStart(dest, "/");

    String key = keyPrefix + dest;
    fireTransferDebug("{keyPreix = " + keyPrefix + ", dest=" + destination + "} -> " + dest);

    // Prepare for meta data
    ObjectMetadata meta = new ObjectMetadata();

    // Content length is important. Many S3 client relies on it
    if (contentLength != -1) {
      meta.setContentLength(contentLength);
    }

    // Last modified data is used by CloudFront
    meta.setLastModified(new Date(lastModified));

    // Find mime type based on file extension
    int lastDot = destination.lastIndexOf('.');
    String mimeType = null;
    if (lastDot != -1) {
      String ext = destination.substring(lastDot + 1, destination.length());
      mimeType = mimeTypes.get(ext);
    }
    if (mimeType == null) {
      mimeType = typeMap.getContentType(destination);
    } else {
      fireTransferDebug(
          "Mime type of " + dest + " is " + mimeType + " according to build-in types");
    }
    if (mimeType != null) {
      meta.setContentType(mimeType);
    }

    fireTransferDebug("Uploading file " + inFile + " to  s3://" + bucketName + "/" + key);
    firePutStarted(resource, inFile);
    s3.putObject(bucketName, key, in, meta);
    firePutCompleted(resource, inFile);
  }

  @Override
  public void fillInputData(InputData in)
      throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
    fireTransferDebug("Filling input data");
    String key = keyPrefix + in.getResource().getName();
    S3Object object;
    try {
      object = s3.getObject(bucketName, key);
    } catch (AmazonServiceException e) {
      if (e.getStatusCode() == 404) {
        throw new ResourceDoesNotExistException(
            "Key " + key + " does not exist in S3 bucket " + bucketName);
      } else if (e.getStatusCode() == 403) {
        // 403 is thrown when key does not exist and configuration
        // doesn't allow user to list keys
        throw new ResourceDoesNotExistException(
            "403 implies that key " + key + " does not exist in bucket " + bucketName, e);
      }
      throw new TransferFailedException("Can't get object " + key + " from S4 bucket " + bucketName,
          e);
    }
    in.getResource().setContentLength(object.getObjectMetadata().getContentLength());
    in.getResource().setLastModified(object.getObjectMetadata().getLastModified().getTime());
    in.setInputStream(object.getObjectContent());
  }

  @Override
  public void fillOutputData(OutputData out) throws TransferFailedException {
    throw new UnsupportedOperationException("This call is not supported");
  }

  @Override
  public void get(String resourceName, File destination)
      throws ResourceDoesNotExistException, TransferFailedException {
    Resource resource = new Resource(resourceName);
    fireGetInitiated(resource, destination);
    String key = keyPrefix + resourceName;
    try {
      fireGetStarted(resource, destination);

      // This is a bit more efficient than copying stream
      s3.getObject(new GetObjectRequest(bucketName, key), destination);
      fireGetCompleted(resource, destination);
    } catch (AmazonServiceException e) {
      if (e.getStatusCode() == 404) {
        throw new ResourceDoesNotExistException(
            "Key " + key + " does not exist in bucket " + bucketName, e);
      } else if (e.getStatusCode() == 403) {
        throw new ResourceDoesNotExistException(
            "403 implies that key " + key + " does not exist in bucket " + bucketName, e);
      }
      throw new TransferFailedException("Getting metadata of key " + key + " failed", e);
    }
  }

  /**
   * @inheritDoc
   */
  public List<String> getFileList(String destinationDirectory)
      throws TransferFailedException, ResourceDoesNotExistException {
    String path = keyPrefix + destinationDirectory;
    if (!path.endsWith("/")) {
      path += "/";
    }
    fireSessionDebug("Listing objects with prefix " + path + " under bucket " + bucketName);

    // Since S3 does not have concept of directory, result contains all
    // contents with given prefix
    ObjectListing result = s3.listObjects(
        new ListObjectsRequest().withBucketName(bucketName).withPrefix(path).withDelimiter("/"));
    if (result.getObjectSummaries().isEmpty()) {
      throw new ResourceDoesNotExistException("No keys exist with prefix " + path);
    }
    Set<String> results = new HashSet<String>();
    for (S3ObjectSummary summary : result.getObjectSummaries()) {
      String name = StringUtils.removeStart(summary.getKey(), path);
      if (name.indexOf('/') == -1) {
        results.add(name);
      } else {
        results.add(name.substring(0, name.indexOf('/')));
      }
    }
    fireSessionDebug("Returning result " + results);
    return new ArrayList<String>(results);
  }

  /**
   * @inheritDoc
   */
  public boolean getIfNewer(String resourceName, File destination, long timestamp)
      throws ResourceDoesNotExistException, TransferFailedException {
    ObjectMetadata meta = getRequiredMetadata(resourceName);
    if (meta == null) {
      return false;
    }
    if (meta.getLastModified() != null && meta.getLastModified().getTime() > timestamp) {
      fireSessionDebug("Remote timestamp " + meta.getLastModified()
          + " is greater than local timestamp " + timestamp + ", ignore get");
      return false;
    }
    get(resourceName, destination);
    return true;
  }

  /**
   * @inheritDoc
   */
  @Override
  public boolean getIfNewerToStream(String resourceName, OutputStream out, long timestamp)
      throws ResourceDoesNotExistException, TransferFailedException {
    ObjectMetadata meta = getRequiredMetadata(resourceName);
    if (meta == null) {
      return false;
    }
    if (meta.getLastModified() != null && meta.getLastModified().getTime() > timestamp) {
      fireSessionDebug("Remote timestamp " + meta.getLastModified()
          + " is greater than local timestamp " + timestamp + ", ignore get");
      return false;
    }
    Resource resource = new Resource(resourceName);
    fireGetInitiated(resource, null);
    try (InputStream in = s3.getObject(bucketName, keyPrefix).getObjectContent()) {
      fireGetStarted(resource, null);
      IOUtils.copy(in, out);
      out.flush();
      out.close();
      fireGetCompleted(resource, null);
      return true;
    } catch (IOException e) {
      throw new TransferFailedException("Stream copy failed", e);
    }
  }

  private ObjectMetadata getRequiredMetadata(String resourceName)
      throws ResourceDoesNotExistException, TransferFailedException {
    String key = keyPrefix + resourceName;
    try {
      return s3.getObjectMetadata(bucketName, key);
    } catch (AmazonServiceException e) {
      if (e.getStatusCode() == 404) {
        throw new ResourceDoesNotExistException(
            "Key " + key + " does not exist in bucket " + bucketName, e);
      }
      throw new TransferFailedException("Getting metadata of key " + key + "failed", e);
    }
  }

  /**
   * @inheritDoc
   */
  @Override
  protected void openConnectionInternal() throws ConnectionException, AuthenticationException {
    AWSCredentialsProvider credentials = new AWSCredentialsProviderChain(
        new EnvironmentVariableCredentialsProvider(), new InstanceProfileCredentialsProvider(true),
        new WagonAuthCredentialsProvider(authenticationInfo));

    // Pass timeout configuration to AWS client config
    ClientConfiguration config = new ClientConfiguration();
    config.setConnectionTimeout(getTimeout());
    config.setSocketTimeout(getTimeout());
    fireSessionDebug("Connect timeout and socket timeout is set to " + getTimeout() + " ms");

    // Possible proxy
    ProxyInfo proxy = getProxyInfo();
    fireSessionDebug("Setting up AWS S3 client with source "
        + ToStringBuilder.reflectionToString(getRepository())
        + ", authentication information and proxy " + ToStringBuilder.reflectionToString(proxy));
    if (proxy != null) {
      config.setProxyDomain(proxy.getNtlmDomain());
      config.setProxyHost(proxy.getHost());
      config.setProxyPassword(proxy.getPassword());
      config.setProxyPort(proxy.getPort());
      config.setProxyUsername(proxy.getUserName());
      config.setProxyWorkstation(proxy.getNtlmHost());
    }
    fireSessionDebug("AWS Client config is " + ToStringBuilder.reflectionToString(config));
    // TODO: At this point the region is hard-coded to US_EAST_1
    // I understand this is very inflexible. A better implementation is open to
    // discuss.
    s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_1).withCredentials(credentials)
        .withClientConfiguration(config).build();
    bucketName = getRepository().getHost();
    fireSessionDebug("Bucket name is " + bucketName);

    // Figure out path defined in pom.xml
    String prefix = StringUtils.trimToEmpty(getRepository().getBasedir());
    if (!prefix.endsWith("/")) {
      prefix = prefix + "/";
    }
    prefix = StringUtils.removeStart(prefix, "/");
    keyPrefix = prefix;
    fireSessionDebug("Key prefix " + keyPrefix);
  }

  /**
   * @inheritDoc
   */
  public void put(File source, String destination)
      throws TransferFailedException, ResourceDoesNotExistException {
    try (InputStream in = new FileInputStream(source)) {
      doPutFromStream(in, source, destination, source.length(), source.lastModified());
    } catch (FileNotFoundException e) {
      throw new ResourceDoesNotExistException("Source file " + source + " does not exist", e);
    } catch (IOException e) {
      throw new RuntimeException("Can't open file " + source, e);
    }
  }

  @Override
  public void putDirectory(File sourceDirectory, String destinationDirectory)
      throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
    if (destinationDirectory.equals(".")) {
      destinationDirectory = "";
    }
    fireTransferDebug(
        "Putting " + sourceDirectory + " to " + destinationDirectory + " which is noop");
    for (File file : sourceDirectory.listFiles()) {
      String dest = StringUtils.isBlank(destinationDirectory) ? file.getName()
          : (destinationDirectory + "/" + file.getName());
      fireTransferDebug("Putting child element " + file + " to " + dest);
      if (file.isDirectory()) {
        putDirectory(file, dest);
      } else {
        put(file, dest);
      }
    }
  }

  @Override
  public void putFromStream(InputStream in, String destination)
      throws TransferFailedException, ResourceDoesNotExistException {
    doPutFromStream(in, null, destination, -1, System.currentTimeMillis());
  }

  @Override
  public void putFromStream(InputStream in, String destination, long contentLength,
      long lastModified) throws TransferFailedException, ResourceDoesNotExistException {
    doPutFromStream(in, null, destination, contentLength, lastModified);
  }

  @Override
  public boolean resourceExists(String resourceName)
      throws TransferFailedException, AuthorizationException {
    String key = keyPrefix + resourceName;
    try {
      s3.getObjectMetadata(bucketName, key);
      return true;
    } catch (AmazonServiceException e) {
      if (e.getStatusCode() == 404) {
        return false;
      }
      throw new TransferFailedException("Can't verify if resource key " + key + " exist or not", e);
    }
  }

  @Override
  public boolean supportsDirectoryCopy() {
    return true;
  }
}
