/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.session;

import static org.apache.iotdb.session.Config.PATH_MATCHER;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.iotdb.rpc.IoTDBRPCException;
import org.apache.iotdb.rpc.RpcUtils;
import org.apache.iotdb.rpc.TSStatusCode;
import org.apache.iotdb.service.rpc.thrift.TSBatchInsertionReq;
import org.apache.iotdb.service.rpc.thrift.TSCloseSessionReq;
import org.apache.iotdb.service.rpc.thrift.TSCreateTimeseriesReq;
import org.apache.iotdb.service.rpc.thrift.TSDeleteDataReq;
import org.apache.iotdb.service.rpc.thrift.TSExecuteBatchStatementResp;
import org.apache.iotdb.service.rpc.thrift.TSExecuteStatementReq;
import org.apache.iotdb.service.rpc.thrift.TSExecuteStatementResp;
import org.apache.iotdb.service.rpc.thrift.TSGetTimeZoneResp;
import org.apache.iotdb.service.rpc.thrift.TSIService;
import org.apache.iotdb.service.rpc.thrift.TSInsertInBatchReq;
import org.apache.iotdb.service.rpc.thrift.TSInsertReq;
import org.apache.iotdb.service.rpc.thrift.TSOpenSessionReq;
import org.apache.iotdb.service.rpc.thrift.TSOpenSessionResp;
import org.apache.iotdb.service.rpc.thrift.TSProtocolVersion;
import org.apache.iotdb.service.rpc.thrift.TSSetTimeZoneReq;
import org.apache.iotdb.service.rpc.thrift.TSStatus;
import org.apache.iotdb.tsfile.file.metadata.enums.CompressionType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.write.record.RowBatch;
import org.apache.iotdb.tsfile.write.schema.MeasurementSchema;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Session {

  private static final Logger logger = LoggerFactory.getLogger(Session.class);
  private final TSProtocolVersion protocolVersion = TSProtocolVersion.IOTDB_SERVICE_PROTOCOL_V1;
  private String host;
  private int port;
  private String username;
  private String password;
  private TSIService.Iface client = null;
  private long sessionId;
  private TSocket transport;
  private boolean isClosed = true;
  private ZoneId zoneId;
  private long statementId;


  public Session(String host, int port) {
    this(host, port, Config.DEFAULT_USER, Config.DEFAULT_PASSWORD);
  }

  public Session(String host, String port, String username, String password) {
    this(host, Integer.parseInt(port), username, password);
  }

  public Session(String host, int port, String username, String password) {
    this.host = host;
    this.port = port;
    this.username = username;
    this.password = password;
  }

  public synchronized void open() throws IoTDBSessionException {
    open(false, 0);
  }

  private synchronized void open(boolean enableRPCCompression, int connectionTimeoutInMs)
      throws IoTDBSessionException {
    if (!isClosed) {
      return;
    }
    transport = new TSocket(host, port, connectionTimeoutInMs);
    if (!transport.isOpen()) {
      try {
        transport.open();
      } catch (TTransportException e) {
        throw new IoTDBSessionException(e);
      }
    }

    if (enableRPCCompression) {
      client = new TSIService.Client(new TCompactProtocol(transport));
    } else {
      client = new TSIService.Client(new TBinaryProtocol(transport));
    }

    TSOpenSessionReq openReq = new TSOpenSessionReq(TSProtocolVersion.IOTDB_SERVICE_PROTOCOL_V1);
    openReq.setUsername(username);
    openReq.setPassword(password);

    try {
      TSOpenSessionResp openResp = client.openSession(openReq);

      RpcUtils.verifySuccess(openResp.getStatus());

      if (protocolVersion.getValue() != openResp.getServerProtocolVersion().getValue()) {
        throw new TException(String
            .format("Protocol not supported, Client version is %s, but Server version is %s",
                protocolVersion.getValue(), openResp.getServerProtocolVersion().getValue()));
      }

      sessionId = openResp.getSessionId();

      statementId = client.requestStatementId(sessionId);

      if (zoneId != null) {
        setTimeZone(zoneId.toString());
      } else {
        zoneId = ZoneId.of(getTimeZone());
      }

    } catch (TException | IoTDBRPCException e) {
      transport.close();
      throw new IoTDBSessionException(String.format("Can not open session to %s:%s with user: %s.",
          host, port, username), e);
    }
    isClosed = false;

    client = RpcUtils.newSynchronizedClient(client);

  }

  public synchronized void close() throws IoTDBSessionException {
    if (isClosed) {
      return;
    }
    TSCloseSessionReq req = new TSCloseSessionReq(sessionId);
    try {
      client.closeSession(req);
    } catch (TException e) {
      throw new IoTDBSessionException(
          "Error occurs when closing session at server. Maybe server is down.", e);
    } finally {
      isClosed = true;
      if (transport != null) {
        transport.close();
      }
    }
  }

  /**
   * use batch interface to insert data
   *
   * @param rowBatch data batch
   */
  public TSExecuteBatchStatementResp insertBatch(RowBatch rowBatch)
      throws IoTDBSessionException {
    TSBatchInsertionReq request = new TSBatchInsertionReq();
    request.setSessionId(sessionId);
    request.deviceId = rowBatch.deviceId;
    for (MeasurementSchema measurementSchema : rowBatch.measurements) {
      request.addToMeasurements(measurementSchema.getMeasurementId());
      request.addToTypes(measurementSchema.getType().ordinal());
    }
    request.setTimestamps(SessionUtils.getTimeBuffer(rowBatch));
    request.setValues(SessionUtils.getValueBuffer(rowBatch));
    request.setSize(rowBatch.batchSize);

    try {
      return checkAndReturn(client.insertBatch(request));
    } catch (TException e) {
      throw new IoTDBSessionException(e);
    }
  }

  /**
   * Insert data in batch format, which can reduce the overhead of network. This method is just like
   * jdbc batch insert, we pack some insert request in batch and send them to server If you want
   * improve your performance, please see insertBatch method
   *
   * @see Session#insertBatch(RowBatch)
   */
  public List<TSStatus> insertInBatch(List<String> deviceIds, List<Long> times,
      List<List<String>> measurementsList,
      List<List<String>> valuesList)
      throws IoTDBSessionException {
    // check params size
    int len = deviceIds.size();
    if (len != times.size() || len != measurementsList.size() || len != valuesList.size()) {
      throw new IllegalArgumentException(
          "deviceIds, times, measurementsList and valuesList's size should be equal");
    }

    TSInsertInBatchReq request = new TSInsertInBatchReq();
    request.setSessionId(sessionId);
    request.setDeviceIds(deviceIds);
    request.setTimestamps(times);
    request.setMeasurementsList(measurementsList);
    request.setValuesList(valuesList);

    try {
      List<TSStatus> result = new ArrayList<>();
      for (TSStatus cur : client.insertRowInBatch(request).getStatusList()) {
        result.add(checkAndReturn(cur));
      }
      return result;
    } catch (TException e) {
      throw new IoTDBSessionException(e);
    }
  }

  /**
   * insert data in one row, if you want improve your performance, please use insertInBatch method
   * or insertBatch method
   *
   * @see Session#insertInBatch(List, List, List, List)
   * @see Session#insertBatch(RowBatch)
   */
  public TSStatus insert(String deviceId, long time, List<String> measurements,
      List<String> values)
      throws IoTDBSessionException {
    TSInsertReq request = new TSInsertReq();
    request.setSessionId(sessionId);
    request.setDeviceId(deviceId);
    request.setTimestamp(time);
    request.setMeasurements(measurements);
    request.setValues(values);

    try {
      return checkAndReturn(client.insertRow(request));
    } catch (TException e) {
      throw new IoTDBSessionException(e);
    }
  }

  /**
   * This method NOT insert data into database and the server just return after accept the request,
   * this method should be used to test other time cost in client
   */
  public TSExecuteBatchStatementResp testInsertBatch(RowBatch rowBatch)
      throws IoTDBSessionException {
    TSBatchInsertionReq request = new TSBatchInsertionReq();
    request.setSessionId(sessionId);
    request.deviceId = rowBatch.deviceId;
    for (MeasurementSchema measurementSchema : rowBatch.measurements) {
      request.addToMeasurements(measurementSchema.getMeasurementId());
      request.addToTypes(measurementSchema.getType().ordinal());
    }
    request.setTimestamps(SessionUtils.getTimeBuffer(rowBatch));
    request.setValues(SessionUtils.getValueBuffer(rowBatch));
    request.setSize(rowBatch.batchSize);

    try {
      return client.testInsertBatch(request);
    } catch (TException e) {
      throw new IoTDBSessionException(e);
    }
  }

  /**
   * This method NOT insert data into database and the server just return after accept the request,
   * this method should be used to test other time cost in client
   */
  public List<TSStatus> testInsertInBatch(List<String> deviceIds, List<Long> times,
      List<List<String>> measurementsList,
      List<List<String>> valuesList)
      throws IoTDBSessionException {
    // check params size
    int len = deviceIds.size();
    if (len != times.size() || len != measurementsList.size() || len != valuesList.size()) {
      throw new IllegalArgumentException(
          "deviceIds, times, measurementsList and valuesList's size should be equal");
    }

    TSInsertInBatchReq request = new TSInsertInBatchReq();
    request.setSessionId(sessionId);
    request.setDeviceIds(deviceIds);
    request.setTimestamps(times);
    request.setMeasurementsList(measurementsList);
    request.setValuesList(valuesList);

    try {
      client.testInsertRowInBatch(request);
      return Collections.emptyList();
    } catch (TException e) {
      throw new IoTDBSessionException(e);
    }
  }

  /**
   * This method NOT insert data into database and the server just return after accept the request,
   * this method should be used to test other time cost in client
   */
  public TSStatus testInsert(String deviceId, long time, List<String> measurements,
      List<String> values)
      throws IoTDBSessionException {
    TSInsertReq request = new TSInsertReq();
    request.setSessionId(sessionId);
    request.setDeviceId(deviceId);
    request.setTimestamp(time);
    request.setMeasurements(measurements);
    request.setValues(values);

    try {
      return client.testInsertRow(request);
    } catch (TException e) {
      throw new IoTDBSessionException(e);
    }
  }

  /**
   * delete a timeseries, including data and schema
   *
   * @param path timeseries to delete, should be a whole path
   */
  public TSStatus deleteTimeseries(String path) throws IoTDBSessionException {
    List<String> paths = new ArrayList<>();
    paths.add(path);
    return deleteTimeseries(paths);
  }

  /**
   * delete a timeseries, including data and schema
   *
   * @param paths timeseries to delete, should be a whole path
   */
  public TSStatus deleteTimeseries(List<String> paths) throws IoTDBSessionException {
    try {
      return checkAndReturn(client.deleteTimeseries(sessionId, paths));
    } catch (TException e) {
      throw new IoTDBSessionException(e);
    }
  }

  /**
   * delete data <= time in one timeseries
   *
   * @param path data in which time series to delete
   * @param time data with time stamp less than or equal to time will be deleted
   */
  public TSStatus deleteData(String path, long time) throws IoTDBSessionException {
    List<String> paths = new ArrayList<>();
    paths.add(path);
    return deleteData(paths, time);
  }

  /**
   * delete data <= time in multiple timeseries
   *
   * @param paths data in which time series to delete
   * @param time data with time stamp less than or equal to time will be deleted
   */
  public TSStatus deleteData(List<String> paths, long time)
      throws IoTDBSessionException {
    TSDeleteDataReq request = new TSDeleteDataReq();
    request.setSessionId(sessionId);
    request.setPaths(paths);
    request.setTimestamp(time);

    try {
      return checkAndReturn(client.deleteData(request));
    } catch (TException e) {
      throw new IoTDBSessionException(e);
    }
  }

  public TSStatus setStorageGroup(String storageGroupId) throws IoTDBSessionException {
    checkPathValidity(storageGroupId);
    try {
      return checkAndReturn(client.setStorageGroup(sessionId, storageGroupId));
    } catch (TException e) {
      throw new IoTDBSessionException(e);
    }
  }


  public TSStatus deleteStorageGroup(String storageGroup)
      throws IoTDBSessionException {
    List<String> groups = new ArrayList<>();
    groups.add(storageGroup);
    return deleteStorageGroups(groups);
  }

  public TSStatus deleteStorageGroups(List<String> storageGroup)
      throws IoTDBSessionException {
    try {
      return checkAndReturn(client.deleteStorageGroups(sessionId, storageGroup));
    } catch (TException e) {
      throw new IoTDBSessionException(e);
    }
  }

  public TSStatus createTimeseries(String path, TSDataType dataType,
      TSEncoding encoding, CompressionType compressor) throws IoTDBSessionException {
    checkPathValidity(path);
    TSCreateTimeseriesReq request = new TSCreateTimeseriesReq();
    request.setSessionId(sessionId);
    request.setPath(path);
    request.setDataType(dataType.ordinal());
    request.setEncoding(encoding.ordinal());
    request.setCompressor(compressor.ordinal());

    try {
      return checkAndReturn(client.createTimeseries(request));
    } catch (TException e) {
      throw new IoTDBSessionException(e);
    }
  }

  private TSStatus checkAndReturn(TSStatus resp) {
    if (resp.statusType.getCode() != TSStatusCode.SUCCESS_STATUS.getStatusCode()) {
      logger.error(resp.statusType.getMessage());
    }
    return resp;
  }

  private TSExecuteBatchStatementResp checkAndReturn(TSExecuteBatchStatementResp resp) {
    if (resp.status.statusType.getCode() != TSStatusCode.SUCCESS_STATUS.getStatusCode()) {
      logger.error(resp.status.statusType.getMessage());
    }
    return resp;
  }

  private synchronized String getTimeZone() throws TException, IoTDBRPCException {
    if (zoneId != null) {
      return zoneId.toString();
    }

    TSGetTimeZoneResp resp = client.getTimeZone(sessionId);
    RpcUtils.verifySuccess(resp.getStatus());
    return resp.getTimeZone();
  }

  private synchronized void setTimeZone(String zoneId) throws TException, IoTDBRPCException {
    TSSetTimeZoneReq req = new TSSetTimeZoneReq(sessionId, zoneId);
    TSStatus resp = client.setTimeZone(req);
    RpcUtils.verifySuccess(resp);
    this.zoneId = ZoneId.of(zoneId);
  }

  /**
   * check whether this sql is for query
   *
   * @param sql sql
   * @return whether this sql is for query
   */
  private boolean checkIsQuery(String sql) {
    sql = sql.trim().toLowerCase();
    return sql.startsWith("select") || sql.startsWith("show") || sql.startsWith("list");
  }

  /**
   * execure query sql
   *
   * @param sql query statement
   * @return result set
   */
  public SessionDataSet executeQueryStatement(String sql)
      throws TException, IoTDBRPCException {
    if (!checkIsQuery(sql)) {
      throw new IllegalArgumentException("your sql \"" + sql
          + "\" is not a query statement, you should use executeNonQueryStatement method instead.");
    }

    TSExecuteStatementReq execReq = new TSExecuteStatementReq(sessionId, sql, statementId);
    TSExecuteStatementResp execResp = client.executeStatement(execReq);

    RpcUtils.verifySuccess(execResp.getStatus());
    return new SessionDataSet(sql, execResp.getColumns(), execResp.getDataTypeList(),
        execResp.getQueryId(), client, sessionId);
  }

  /**
   * execute non query statement
   *
   * @param sql non query statement
   */
  public void executeNonQueryStatement(String sql) throws TException, IoTDBRPCException {
    if (checkIsQuery(sql)) {
      throw new IllegalArgumentException("your sql \"" + sql
          + "\" is a query statement, you should use executeQueryStatement method instead.");
    }

    TSExecuteStatementReq execReq = new TSExecuteStatementReq(sessionId, sql, statementId);
    TSExecuteStatementResp execResp = client.executeUpdateStatement(execReq);
    RpcUtils.verifySuccess(execResp.getStatus());
  }

  private void checkPathValidity(String path) throws IoTDBSessionException {
    if (!Pattern.matches(PATH_MATCHER, path)) {
      throw new IoTDBSessionException(
          String.format("Path [%s] is invalid", StringEscapeUtils.escapeJava(path)));
    }
  }

}
