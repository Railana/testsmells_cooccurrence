/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hive.ql.parse;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.net.URLCodec;
import org.apache.hadoop.hive.conf.HiveConf.StrictChecks;

import org.apache.hadoop.hive.conf.HiveConf.ConfVars;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.antlr.runtime.tree.Tree;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.TableType;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.ql.ErrorMsg;
import org.apache.hadoop.hive.ql.QueryState;
import org.apache.hadoop.hive.ql.exec.Task;
import org.apache.hadoop.hive.ql.exec.TaskFactory;
import org.apache.hadoop.hive.ql.exec.Utilities;
import org.apache.hadoop.hive.ql.hooks.WriteEntity;
import org.apache.hadoop.hive.ql.io.HiveFileFormatUtils;
import org.apache.hadoop.hive.ql.metadata.Hive;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.metadata.Partition;
import org.apache.hadoop.hive.ql.plan.LoadTableDesc;
import org.apache.hadoop.hive.ql.plan.MoveWork;
import org.apache.hadoop.hive.ql.plan.StatsWork;
import org.apache.hadoop.mapred.InputFormat;

import com.google.common.collect.Lists;

/**
 * LoadSemanticAnalyzer.
 *
 */
public class LoadSemanticAnalyzer extends BaseSemanticAnalyzer {

  public LoadSemanticAnalyzer(QueryState queryState) throws SemanticException {
    super(queryState);
  }

  public static FileStatus[] matchFilesOrDir(FileSystem fs, Path path)
      throws IOException {
    FileStatus[] srcs = fs.globStatus(path, new PathFilter() {
      @Override
      public boolean accept(Path p) {
        String name = p.getName();
        return name.equals(EximUtil.METADATA_NAME) ? true : !name.startsWith("_") && !name.startsWith(".");
      }
    });
    if ((srcs != null) && srcs.length == 1) {
      if (srcs[0].isDir()) {
        srcs = fs.listStatus(srcs[0].getPath(), new PathFilter() {
          @Override
          public boolean accept(Path p) {
            String name = p.getName();
            return !name.startsWith("_") && !name.startsWith(".");
          }
        });
      }
    }
    return (srcs);
  }

  private URI initializeFromURI(String fromPath, boolean isLocal)
      throws IOException, URISyntaxException, SemanticException {
    URI fromURI = new Path(fromPath).toUri();

    String fromScheme = fromURI.getScheme();
    String fromAuthority = fromURI.getAuthority();
    String path = fromURI.getPath();

    // generate absolute path relative to current directory or hdfs home
    // directory
    if (!path.startsWith("/")) {
      if (isLocal) {
        try {
          path = new String(URLCodec.decodeUrl(
              new Path(System.getProperty("user.dir"), fromPath).toUri().toString()
                  .getBytes("US-ASCII")), "US-ASCII");
        } catch (DecoderException de) {
          throw new SemanticException("URL Decode failed", de);
        }
      } else {
        path = new Path(new Path("/user/" + System.getProperty("user.name")),
          path).toString();
      }
    }

    // set correct scheme and authority
    if (StringUtils.isEmpty(fromScheme)) {
      if (isLocal) {
        // file for local
        fromScheme = "file";
      } else {
        // use default values from fs.default.name
        URI defaultURI = FileSystem.get(conf).getUri();
        fromScheme = defaultURI.getScheme();
        fromAuthority = defaultURI.getAuthority();
      }
    }

    // if scheme is specified but not authority then use the default authority
    if ((!fromScheme.equals("file")) && StringUtils.isEmpty(fromAuthority)) {
      URI defaultURI = FileSystem.get(conf).getUri();
      fromAuthority = defaultURI.getAuthority();
    }

    LOG.debug(fromScheme + "@" + fromAuthority + "@" + path);
    return new URI(fromScheme, fromAuthority, path, null, null);
  }

  private List<FileStatus> applyConstraintsAndGetFiles(URI fromURI, Tree ast,
      boolean isLocal) throws SemanticException {

    FileStatus[] srcs = null;

    // local mode implies that scheme should be "file"
    // we can change this going forward
    if (isLocal && !fromURI.getScheme().equals("file")) {
      throw new SemanticException(ErrorMsg.ILLEGAL_PATH.getMsg(ast,
          "Source file system should be \"file\" if \"local\" is specified"));
    }

    try {
      srcs = matchFilesOrDir(FileSystem.get(fromURI, conf), new Path(fromURI));
      if (srcs == null || srcs.length == 0) {
        throw new SemanticException(ErrorMsg.INVALID_PATH.getMsg(ast,
            "No files matching path " + fromURI));
      }

      for (FileStatus oneSrc : srcs) {
        if (oneSrc.isDir()) {
          throw new SemanticException(ErrorMsg.INVALID_PATH.getMsg(ast,
              "source contains directory: " + oneSrc.getPath().toString()));
        }
      }
    } catch (IOException e) {
      // Has to use full name to make sure it does not conflict with
      // org.apache.commons.lang3.StringUtils
      throw new SemanticException(ErrorMsg.INVALID_PATH.getMsg(ast), e);
    }

    return Lists.newArrayList(srcs);
  }

  @Override
  public void analyzeInternal(ASTNode ast) throws SemanticException {
    boolean isLocal = false;
    boolean isOverWrite = false;
    Tree fromTree = ast.getChild(0);
    Tree tableTree = ast.getChild(1);

    if (ast.getChildCount() == 4) {
      isLocal = true;
      isOverWrite = true;
    }

    if (ast.getChildCount() == 3) {
      if (ast.getChild(2).getText().toLowerCase().equals("local")) {
        isLocal = true;
      } else {
        isOverWrite = true;
      }
    }

    // initialize load path
    URI fromURI;
    try {
      String fromPath = stripQuotes(fromTree.getText());
      fromURI = initializeFromURI(fromPath, isLocal);
    } catch (IOException e) {
      throw new SemanticException(ErrorMsg.INVALID_PATH.getMsg(fromTree, e
          .getMessage()), e);
    } catch (URISyntaxException e) {
      throw new SemanticException(ErrorMsg.INVALID_PATH.getMsg(fromTree, e
          .getMessage()), e);
    }

    // initialize destination table/partition
    TableSpec ts = new TableSpec(db, conf, (ASTNode) tableTree);

    if (ts.tableHandle.isView() || ts.tableHandle.isMaterializedView()) {
      throw new SemanticException(ErrorMsg.DML_AGAINST_VIEW.getMsg());
    }
    if (ts.tableHandle.isNonNative()) {
      throw new SemanticException(ErrorMsg.LOAD_INTO_NON_NATIVE.getMsg());
    }

    if(ts.tableHandle.isStoredAsSubDirectories()) {
      throw new SemanticException(ErrorMsg.LOAD_INTO_STORED_AS_DIR.getMsg());
    }

    List<FieldSchema> parts = ts.tableHandle.getPartitionKeys();
    if ((parts != null && parts.size() > 0)
        && (ts.partSpec == null || ts.partSpec.size() == 0)) {
      throw new SemanticException(ErrorMsg.NEED_PARTITION_ERROR.getMsg());
    }
    List<String> bucketCols = ts.tableHandle.getBucketCols();
    if (bucketCols != null && !bucketCols.isEmpty()) {
      String error = StrictChecks.checkBucketing(conf);
      if (error != null) throw new SemanticException("Please load into an intermediate table"
          + " and use 'insert... select' to allow Hive to enforce bucketing. " + error);
    }

    // make sure the arguments make sense
    List<FileStatus> files = applyConstraintsAndGetFiles(fromURI, fromTree, isLocal);

    // for managed tables, make sure the file formats match
    if (TableType.MANAGED_TABLE.equals(ts.tableHandle.getTableType())
        && conf.getBoolVar(HiveConf.ConfVars.HIVECHECKFILEFORMAT)) {
      ensureFileFormatsMatch(ts, files, fromURI);
    }
    inputs.add(toReadEntity(new Path(fromURI)));
    Task<? extends Serializable> rTask = null;

    // create final load/move work

    boolean preservePartitionSpecs = false;

    Map<String, String> partSpec = ts.getPartSpec();
    if (partSpec == null) {
      partSpec = new LinkedHashMap<String, String>();
      outputs.add(new WriteEntity(ts.tableHandle,
          (isOverWrite ? WriteEntity.WriteType.INSERT_OVERWRITE :
              WriteEntity.WriteType.INSERT)));
    } else {
      try{
        Partition part = Hive.get().getPartition(ts.tableHandle, partSpec, false);
        if (part != null) {
          if (isOverWrite){
            outputs.add(new WriteEntity(part, WriteEntity.WriteType.INSERT_OVERWRITE));
          } else {
            outputs.add(new WriteEntity(part, WriteEntity.WriteType.INSERT));
            // If partition already exists and we aren't overwriting it, then respect
            // its current location info rather than picking it from the parent TableDesc
            preservePartitionSpecs = true;
          }
        } else {
          outputs.add(new WriteEntity(ts.tableHandle,
          (isOverWrite ? WriteEntity.WriteType.INSERT_OVERWRITE :
              WriteEntity.WriteType.INSERT)));
        }
      } catch(HiveException e) {
        throw new SemanticException(e);
      }
    }


    LoadTableDesc loadTableWork;
    loadTableWork = new LoadTableDesc(new Path(fromURI),
      Utilities.getTableDesc(ts.tableHandle), partSpec, isOverWrite);
    if (preservePartitionSpecs){
      // Note : preservePartitionSpecs=true implies inheritTableSpecs=false but
      // but preservePartitionSpecs=false(default) here is not sufficient enough
      // info to set inheritTableSpecs=true
      loadTableWork.setInheritTableSpecs(false);
    }

    Task<? extends Serializable> childTask = TaskFactory.get(new MoveWork(getInputs(),
        getOutputs(), loadTableWork, null, true, isLocal), conf);
    if (rTask != null) {
      rTask.addDependentTask(childTask);
    } else {
      rTask = childTask;
    }

    rootTasks.add(rTask);

    // The user asked for stats to be collected.
    // Some stats like number of rows require a scan of the data
    // However, some other stats, like number of files, do not require a complete scan
    // Update the stats which do not require a complete scan.
    Task<? extends Serializable> statTask = null;
    if (conf.getBoolVar(HiveConf.ConfVars.HIVESTATSAUTOGATHER)) {
      StatsWork statDesc = new StatsWork(loadTableWork);
      statDesc.setNoStatsAggregator(true);
      statDesc.setClearAggregatorStats(true);
      statDesc.setStatsReliable(conf.getBoolVar(HiveConf.ConfVars.HIVE_STATS_RELIABLE));
      statTask = TaskFactory.get(statDesc, conf);
    }

    // HIVE-3334 has been filed for load file with index auto update
    if (HiveConf.getBoolVar(conf, HiveConf.ConfVars.HIVEINDEXAUTOUPDATE)) {
      IndexUpdater indexUpdater = new IndexUpdater(loadTableWork, getInputs(), conf);
      try {
        List<Task<? extends Serializable>> indexUpdateTasks = indexUpdater.generateUpdateTasks();

        for (Task<? extends Serializable> updateTask : indexUpdateTasks) {
          //LOAD DATA will either have a copy & move or just a move,
          // we always want the update to be dependent on the move
          childTask.addDependentTask(updateTask);
          if (statTask != null) {
            updateTask.addDependentTask(statTask);
          }
        }
      } catch (HiveException e) {
        console.printInfo("WARNING: could not auto-update stale indexes, indexes are not out of sync");
      }
    }
    else if (statTask != null) {
      childTask.addDependentTask(statTask);
    }
  }

  private void ensureFileFormatsMatch(TableSpec ts, List<FileStatus> fileStatuses,
      final URI fromURI)
      throws SemanticException {
    final Class<? extends InputFormat> destInputFormat;
    try {
      if (ts.getPartSpec() == null || ts.getPartSpec().isEmpty()) {
        destInputFormat = ts.tableHandle.getInputFormatClass();
      } else {
        destInputFormat = ts.partHandle.getInputFormatClass();
      }
    } catch (HiveException e) {
      throw new SemanticException(e);
    }

    try {
      FileSystem fs = FileSystem.get(fromURI, conf);
      boolean validFormat = HiveFileFormatUtils.checkInputFormat(fs, conf, destInputFormat,
          fileStatuses);
      if (!validFormat) {
        throw new SemanticException(ErrorMsg.INVALID_FILE_FORMAT_IN_LOAD.getMsg());
      }
    } catch (Exception e) {
      throw new SemanticException("Unable to load data to destination table." +
          " Error: " + e.getMessage());
    }
  }
}
