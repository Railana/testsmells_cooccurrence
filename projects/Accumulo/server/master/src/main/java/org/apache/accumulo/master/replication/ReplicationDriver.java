/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.accumulo.master.replication;

import org.apache.accumulo.core.client.AccumuloException;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.conf.AccumuloConfiguration;
import org.apache.accumulo.core.conf.Property;
import org.apache.accumulo.core.trace.ProbabilitySampler;
import org.apache.accumulo.core.trace.Trace;
import org.apache.accumulo.core.util.Daemon;
import org.apache.accumulo.fate.util.UtilWaitThread;
import org.apache.accumulo.master.Master;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Daemon wrapper around the {@link WorkMaker} that separates it from the Master
 */
public class ReplicationDriver extends Daemon {
  private static final Logger log = LoggerFactory.getLogger(ReplicationDriver.class);

  private final Master master;
  private final AccumuloConfiguration conf;

  private WorkMaker workMaker;
  private StatusMaker statusMaker;
  private FinishedWorkUpdater finishedWorkUpdater;
  private RemoveCompleteReplicationRecords rcrr;
  private Connector conn;

  public ReplicationDriver(Master master) {
    super("Replication Driver");

    this.master = master;
    this.conf = master.getConfiguration();
  }

  @Override
  public void run() {
    ProbabilitySampler sampler =
        new ProbabilitySampler(conf.getFraction(Property.REPLICATION_TRACE_PERCENT));

    long millisToWait = conf.getTimeInMillis(Property.REPLICATION_DRIVER_DELAY);
    log.debug("Waiting " + millisToWait + "ms before starting main replication loop");
    UtilWaitThread.sleep(millisToWait);

    log.debug("Starting replication loop");

    while (master.stillMaster()) {
      if (null == workMaker) {
        try {
          conn = master.getConnector();
        } catch (AccumuloException | AccumuloSecurityException e) {
          // couldn't get a connector, try again in a "short" amount of time
          log.warn("Error trying to get connector to process replication records", e);
          UtilWaitThread.sleep(2000);
          continue;
        }

        statusMaker = new StatusMaker(conn, master.getFileSystem());
        workMaker = new WorkMaker(master, conn);
        finishedWorkUpdater = new FinishedWorkUpdater(conn);
        rcrr = new RemoveCompleteReplicationRecords(conn);
      }

      Trace.on("masterReplicationDriver", sampler);

      // Make status markers from replication records in metadata, removing entries in
      // metadata which are no longer needed (closed records)
      // This will end up creating the replication table too
      try {
        statusMaker.run();
      } catch (Exception e) {
        log.error("Caught Exception trying to create Replication status records", e);
      }

      // Tell the work maker to make work
      try {
        workMaker.run();
      } catch (Exception e) {
        log.error("Caught Exception trying to create Replication work records", e);
      }

      // Update the status records from the work records
      try {
        finishedWorkUpdater.run();
      } catch (Exception e) {
        log.error(
            "Caught Exception trying to update Replication records using finished work records", e);
      }

      // Clean up records we no longer need.
      // It must be running at the same time as the StatusMaker or WorkMaker
      // So it's important that we run these sequentially and not concurrently
      try {
        rcrr.run();
      } catch (Exception e) {
        log.error("Caught Exception trying to remove finished Replication records", e);
      }

      Trace.off();

      // Sleep for a bit
      long sleepMillis = conf.getTimeInMillis(Property.MASTER_REPLICATION_SCAN_INTERVAL);
      log.trace("Sleeping for {}ms before re-running", sleepMillis);
      try {
        Thread.sleep(sleepMillis);
      } catch (InterruptedException e) {
        log.error("Interrupted while sleeping", e);
      }
    }
  }
}
