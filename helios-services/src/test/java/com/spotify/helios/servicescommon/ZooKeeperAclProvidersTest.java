/*
 * Copyright (c) 2016 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.helios.servicescommon;

import com.spotify.helios.servicescommon.coordination.Paths;

import org.apache.curator.framework.api.ACLProvider;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static com.spotify.helios.servicescommon.ZooKeeperAclProviders.digest;
import static org.apache.zookeeper.ZooDefs.Perms.CREATE;
import static org.apache.zookeeper.ZooDefs.Perms.DELETE;
import static org.apache.zookeeper.ZooDefs.Perms.READ;
import static org.apache.zookeeper.ZooDefs.Perms.WRITE;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class ZooKeeperAclProvidersTest {

  private static final String DIGEST_SCHEME = "digest";
  private static final String AGENT_USER = "agent-user";
  private static final String AGENT_PASSWORD = "agent-pass";
  private static final String AGENT_DIGEST = digest(AGENT_USER, AGENT_PASSWORD);
  private static final Id AGENT_ID = new Id(DIGEST_SCHEME, AGENT_USER + ":" + AGENT_DIGEST);
  private static final String MASTER_USER = "master-user";
  private static final String MASTER_PASSWORD = "master-pass";
  private static final String MASTER_DIGEST = digest(MASTER_USER, MASTER_PASSWORD);
  private static final Id MASTER_ID = new Id(DIGEST_SCHEME, MASTER_USER + ":" + MASTER_DIGEST);

  private ACLProvider aclProvider;

  @Before
  public void setUp() {
    aclProvider = ZooKeeperAclProviders.heliosAclProvider(
        MASTER_USER, MASTER_DIGEST,
        AGENT_USER, AGENT_DIGEST);
  }

  @Test
  public void testDigest() {
    // Reference test value generated by running:
    // echo -n user:password | openssl dgst -sha1 -binary | base64
    assertEquals("tpUq/4Pn5A64fVZyQ0gOJ8ZWqkY=", digest("user", "password"));
  }

  // Everyone should have READ permission on ALL nodes
  @Test
  public void testEveryoneHasReadPermissionEverywhere() {
    final ACL acl = new ACL(READ, ZooDefs.Ids.ANYONE_ID_UNSAFE);
    assertThat(aclProvider.getAclForPath("/"), hasItem(acl));
    assertThat(aclProvider.getAclForPath("/some/random/path"), hasItem(acl));
  }

  // Masters should have CRWD permissions on ALL nodes
  @Test
  public void testMastersHaveCrwdPermissionsEverywhere() {
    final ACL acl = new ACL(CREATE | READ | WRITE | DELETE, MASTER_ID);
    assertThat(aclProvider.getAclForPath("/"), hasItem(acl));
    assertThat(aclProvider.getAclForPath("/some/random/path"), hasItem(acl));
  }

  @Test
  public void testAgentPermissions() {
    // Verify that agents only have READ permissions on paths it shouldn't meddle with
    // (these tests are obviously not exhaustive)
    assertEquals(agentPerms(aclProvider.getAclForPath("/")), READ);
    assertEquals(agentPerms(aclProvider.getAclForPath("/random/path")), READ);
    assertEquals(agentPerms(aclProvider.getAclForPath("/config")), READ);
    assertEquals(agentPerms(aclProvider.getAclForPath("/status")), READ);
    assertEquals(agentPerms(aclProvider.getAclForPath(Paths.statusDeploymentGroupTasks())), READ);
    assertEquals(agentPerms(aclProvider.getAclForPath(Paths.configDeploymentGroups())), READ);
    assertEquals(agentPerms(aclProvider.getAclForPath(Paths.configDeploymentGroup("group"))), READ);
    assertEquals(agentPerms(aclProvider.getAclForPath(Paths.configHostJobs("host"))), READ);

    // Agents need limited permissions in the /config/hosts subtree
    assertEquals(agentPerms(aclProvider.getAclForPath(Paths.configHosts())),
                 CREATE | READ | DELETE);
    assertEquals(agentPerms(aclProvider.getAclForPath(Paths.configHost("host"))),
                 CREATE | READ | DELETE);
    assertEquals(agentPerms(aclProvider.getAclForPath(Paths.configHostId("host"))),
                 CREATE | READ | DELETE);
    assertEquals(agentPerms(aclProvider.getAclForPath(Paths.configHostPorts("host"))),
                 CREATE | READ | DELETE);
    assertEquals(agentPerms(aclProvider.getAclForPath(Paths.configHostPort("host", 123))),
                 READ);

    // Agents need elevated permissions in the /status/hosts subtree
    assertEquals(agentPerms(aclProvider.getAclForPath(Paths.statusHosts())),
                 CREATE | READ | DELETE);
    assertEquals(agentPerms(aclProvider.getAclForPath(Paths.statusHost("host"))),
                 CREATE | READ | DELETE);
    assertEquals(agentPerms(aclProvider.getAclForPath(Paths.statusHostJobs("host"))),
                 CREATE | READ | DELETE);
    assertEquals(agentPerms(aclProvider.getAclForPath(Paths.statusHostJob("host", "job"))),
                 READ | WRITE);
    assertEquals(agentPerms(aclProvider.getAclForPath(Paths.statusHostAgentInfo("host"))),
                 READ | WRITE);
    assertEquals(agentPerms(aclProvider.getAclForPath(Paths.statusHostLabels("host"))),
                 READ | WRITE);
    assertEquals(agentPerms(aclProvider.getAclForPath(Paths.statusHostEnvVars("host"))),
                 READ | WRITE);
    assertEquals(agentPerms(aclProvider.getAclForPath(Paths.statusHostUp("host"))),
                 READ | WRITE);
  }

  private static int agentPerms(final List<ACL> acls) {
    for (final ACL acl : acls) {
      if (acl.getId().equals(AGENT_ID)) {
        return acl.getPerms();
      }
    }
    return 0;
  }
}