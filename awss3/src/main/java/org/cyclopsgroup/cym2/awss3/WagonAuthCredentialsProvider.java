package org.cyclopsgroup.cym2.awss3;

import javax.annotation.Nullable;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;

class WagonAuthCredentialsProvider implements AWSCredentialsProvider {
  @Nullable
  private final AuthenticationInfo authenticationInfo;

  WagonAuthCredentialsProvider(AuthenticationInfo authInfo) {
    this.authenticationInfo = authInfo;
  }

  @Override
  public AWSCredentials getCredentials() {
    // Retrieve credentials from authentication information is always
    // required since it has access key and secret
    // key
    if (authenticationInfo == null) {
      throw new IllegalStateException("No authentication information is specified.");
    }

    // Raise a failure if username is not specified
    if (StringUtils.isEmpty(authenticationInfo.getUserName())) {
      throw new IllegalStateException(
          "Tag <username> isn't specified with AWS access key ID in pom.xml or settings.xml.");
    }

    // Raise a failure if password is not specified
    if (StringUtils.isEmpty(authenticationInfo.getPassword())) {
      throw new IllegalStateException(
          "Tag <password> isn't specified with AWS secret key in pom.xml or settings.xml.");
    }
    return new BasicAWSCredentials(authenticationInfo.getUserName(),
        authenticationInfo.getPassword());
  }

  @Override
  public void refresh() {}
}
