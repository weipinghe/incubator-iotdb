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
package org.apache.iotdb.tsfile.read.reader.series;

import java.io.IOException;
import java.util.List;
import org.apache.iotdb.tsfile.file.metadata.ChunkMetaData;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.read.common.BatchData;
import org.apache.iotdb.tsfile.read.common.Chunk;
import org.apache.iotdb.tsfile.read.controller.IChunkLoader;
import org.apache.iotdb.tsfile.read.reader.chunk.ChunkReader;
import org.apache.iotdb.tsfile.read.reader.chunk.ChunkReaderByTimestamp;

/**
 * <p>
 * Series reader is used to query one series of one tsfile, using this reader to query the value of
 * a series with given timestamps.
 * </p>
 */
public class FileSeriesReaderByTimestamp {

  protected IChunkLoader chunkLoader;
  protected List<ChunkMetaData> chunkMetaDataList;
  private int currentChunkIndex = 0;

  private ChunkReader chunkReader;
  private long currentTimestamp;
  private BatchData data = null; // current batch data

  /**
   * init with chunkLoader and chunkMetaDataList.
   */
  public FileSeriesReaderByTimestamp(IChunkLoader chunkLoader, List<ChunkMetaData> chunkMetaDataList) {
    this.chunkLoader = chunkLoader;
    this.chunkMetaDataList = chunkMetaDataList;
    currentTimestamp = Long.MIN_VALUE;
  }

  public TSDataType getDataType() {
    return chunkMetaDataList.get(0).getDataType();
  }

  /**
   * get value with time equals timestamp. If there is no such point, return null.
   */
  public Object getValueInTimestamp(long timestamp) throws IOException {
    this.currentTimestamp = timestamp;

    // first initialization, only invoked in the first time
    if (chunkReader == null) {
      if (!constructNextSatisfiedChunkReader()) {
        return null;
      }

      if (chunkReader.hasNextBatch()) {
        data = chunkReader.nextBatch();
      } else {
        return null;
      }
    }

    while (data != null) {
      while (data.hasNext()) {
        if (data.currentTime() < timestamp) {
          data.next();
        } else {
          break;
        }
      }

      if (data.hasNext()) {
        if (data.currentTime() == timestamp) {
          Object value = data.currentValue();
          data.next();
          return value;
        }
        return null;
      } else {
        if (chunkReader.hasNextBatch()) {
          data = chunkReader.nextBatch();
        } else if (!constructNextSatisfiedChunkReader()) {
          return null;
        }
      }
    }

    return null;
  }

  /**
   * Judge if the series reader has next time-value pair.
   *
   * @return true if has next, false if not.
   */
  public boolean hasNext() throws IOException {

    if (chunkReader != null) {
      if (data != null && data.hasNext()) {
        return true;
      }
      while (chunkReader.hasNextBatch()) {
        data = chunkReader.nextBatch();
        if (data != null && data.hasNext()) {
          return true;
        }
      }
    }
    while (constructNextSatisfiedChunkReader()) {
      while (chunkReader.hasNextBatch()) {
        data = chunkReader.nextBatch();
        if (data != null && data.hasNext()) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean constructNextSatisfiedChunkReader() throws IOException {
    while (currentChunkIndex < chunkMetaDataList.size()) {
      ChunkMetaData chunkMetaData = chunkMetaDataList.get(currentChunkIndex++);
      if (chunkSatisfied(chunkMetaData)) {
        initChunkReader(chunkMetaData);
        ((ChunkReaderByTimestamp) chunkReader).setCurrentTimestamp(currentTimestamp);
        return true;
      }
    }
    return false;
  }

  private void initChunkReader(ChunkMetaData chunkMetaData) throws IOException {
    Chunk chunk = chunkLoader.getChunk(chunkMetaData);
    this.chunkReader = new ChunkReaderByTimestamp(chunk);
  }

  private boolean chunkSatisfied(ChunkMetaData chunkMetaData) {
    return chunkMetaData.getEndTime() >= currentTimestamp;
  }

}
