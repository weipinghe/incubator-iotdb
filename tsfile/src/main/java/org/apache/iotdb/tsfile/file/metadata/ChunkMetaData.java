/*
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
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.tsfile.file.metadata;

import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.statistics.Statistics;
import org.apache.iotdb.tsfile.utils.ReadWriteIOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * MetaData of one chunk.
 */
public class ChunkMetaData {

  private static final Logger LOG = LoggerFactory.getLogger(ChunkMetaData.class);


  private String measurementUid;

  /**
   * Byte offset of the corresponding data in the file Notice: include the chunk header and marker.
   */
  private long offsetOfChunkHeader;

  private long numOfPoints;

  private long startTime;

  private long endTime;

  private TSDataType tsDataType;

  /**
   * version is used to define the order of operations(insertion, deletion, update). version is set
   * according to its belonging ChunkGroup only when being queried, so it is not persisted.
   */
  private long version;

  /**
   * All data with timestamp <= deletedAt are considered deleted.
   */
  private long deletedAt = Long.MIN_VALUE;

  private Statistics valuesStatistics;

  private ChunkMetaData() {
  }

  /**
   * constructor of ChunkMetaData.
   *
   * @param measurementUid measurement id
   * @param tsDataType time series data type
   * @param fileOffset file offset
   * @param startTime chunk start time
   * @param endTime chunk end time
   */
  public ChunkMetaData(String measurementUid, TSDataType tsDataType, long fileOffset,
      long startTime, long endTime) {
    this.measurementUid = measurementUid;
    this.tsDataType = tsDataType;
    this.offsetOfChunkHeader = fileOffset;
    this.startTime = startTime;
    this.endTime = endTime;
  }

  @Override
  public String toString() {
    return String.format("numPoints %d", numOfPoints);
  }

  public long getNumOfPoints() {
    return numOfPoints;
  }

  public void setNumOfPoints(long numRows) {
    this.numOfPoints = numRows;
  }

  /**
   * get offset of chunk header.
   *
   * @return Byte offset of header of this chunk (includes the marker)
   */
  public long getOffsetOfChunkHeader() {
    return offsetOfChunkHeader;
  }

  public String getMeasurementUid() {
    return measurementUid;
  }

  public Statistics getStatistics() {
    return valuesStatistics;
  }

  public void setStatistics(Statistics statistics) {
    this.valuesStatistics = statistics;

  }

  public long getStartTime() {
    return startTime;
  }

  public void setStartTime(long startTime) {
    this.startTime = startTime;
  }

  public long getEndTime() {
    return endTime;
  }

  public void setEndTime(long endTime) {
    this.endTime = endTime;
  }

  public TSDataType getTsDataType() {
    return tsDataType;
  }

  /**
   * serialize to outputStream.
   *
   * @param outputStream outputStream
   * @return length
   * @throws IOException IOException
   */
  public int serializeTo(OutputStream outputStream) throws IOException {
    int byteLen = 0;

    byteLen += ReadWriteIOUtils.write(measurementUid, outputStream);
    byteLen += ReadWriteIOUtils.write(offsetOfChunkHeader, outputStream);
    byteLen += ReadWriteIOUtils.write(numOfPoints, outputStream);
    byteLen += ReadWriteIOUtils.write(startTime, outputStream);
    byteLen += ReadWriteIOUtils.write(endTime, outputStream);
    byteLen += ReadWriteIOUtils.write(tsDataType, outputStream);

    if (valuesStatistics == null) {
      byteLen += Statistics.serializeNullTo(outputStream);
    } else {
      byteLen += valuesStatistics.serialize(outputStream);
    }
    return byteLen;
  }

  /**
   * deserialize from ByteBuffer.
   *
   * @param buffer ByteBuffer
   * @return ChunkMetaData object
   */
  public static ChunkMetaData deserializeFrom(ByteBuffer buffer) {
    ChunkMetaData chunkMetaData = new ChunkMetaData();

    chunkMetaData.measurementUid = ReadWriteIOUtils.readString(buffer);
    chunkMetaData.offsetOfChunkHeader = ReadWriteIOUtils.readLong(buffer);
    chunkMetaData.numOfPoints = ReadWriteIOUtils.readLong(buffer);
    chunkMetaData.startTime = ReadWriteIOUtils.readLong(buffer);
    chunkMetaData.endTime = ReadWriteIOUtils.readLong(buffer);
    chunkMetaData.tsDataType = ReadWriteIOUtils.readDataType(buffer);

    chunkMetaData.valuesStatistics = Statistics.deserializeFrom(buffer, chunkMetaData.tsDataType);

    return chunkMetaData;
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
  }

  public long getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(long deletedAt) {
    this.deletedAt = deletedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ChunkMetaData that = (ChunkMetaData) o;
    return offsetOfChunkHeader == that.offsetOfChunkHeader &&
        numOfPoints == that.numOfPoints &&
        startTime == that.startTime &&
        endTime == that.endTime &&
        version == that.version &&
        deletedAt == that.deletedAt &&
        Objects.equals(measurementUid, that.measurementUid) &&
        tsDataType == that.tsDataType &&
        Objects.equals(valuesStatistics, that.valuesStatistics);
  }
}
