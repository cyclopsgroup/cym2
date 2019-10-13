package org.cyclopsgroup.cym2.awss3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.junit.jupiter.api.Test;
import com.amazonaws.auth.AWSCredentials;

public class WagonAuthCredentialsProviderTest {

  @Test
  public void testCorrectly() {
    AuthenticationInfo auth = new AuthenticationInfo();
    auth.setUserName("KEY");
    auth.setPassword("SECRET");
    WagonAuthCredentialsProvider p = new WagonAuthCredentialsProvider(auth);
    AWSCredentials creds = p.getCredentials();
    assertEquals("KEY", creds.getAWSAccessKeyId());
    assertEquals("SECRET", creds.getAWSSecretKey());
  }

  @Test
  public void testWithNullAuthInfo() {
    WagonAuthCredentialsProvider p = new WagonAuthCredentialsProvider(null);
    assertThrows(IllegalStateException.class, p::getCredentials);
  }

  @Test
  public void testWithoutKey() {
    WagonAuthCredentialsProvider p = new WagonAuthCredentialsProvider(new AuthenticationInfo());
    assertThrows(IllegalStateException.class, p::getCredentials);
  }

  @Test
  public void testWithoutSecret() {
    AuthenticationInfo auth = new AuthenticationInfo();
    auth.setUserName("KEY");
    WagonAuthCredentialsProvider p = new WagonAuthCredentialsProvider(auth);
    assertThrows(IllegalStateException.class, p::getCredentials);
  }
}
