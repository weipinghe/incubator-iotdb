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

package org.apache.iotdb.db.query.reader.resourceRelated;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.iotdb.db.engine.cache.DeviceMetaDataCache;
import org.apache.iotdb.db.engine.modification.Modification;
import org.apache.iotdb.db.engine.storagegroup.TsFileResource;
import org.apache.iotdb.db.query.context.QueryContext;
import org.apache.iotdb.db.query.control.FileReaderManager;
import org.apache.iotdb.db.query.reader.chunkRelated.ChunkReaderWrap;
import org.apache.iotdb.db.query.reader.universal.PriorityMergeReader;
import org.apache.iotdb.db.utils.QueryUtils;
import org.apache.iotdb.db.utils.TimeValuePair;
import org.apache.iotdb.tsfile.file.metadata.ChunkMetaData;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.read.TsFileSequenceReader;
import org.apache.iotdb.tsfile.read.common.BatchData;
import org.apache.iotdb.tsfile.read.common.Path;
import org.apache.iotdb.tsfile.read.controller.ChunkLoaderImpl;
import org.apache.iotdb.tsfile.read.filter.basic.Filter;
import org.apache.iotdb.tsfile.read.reader.IBatchReader;

/**
 * To read a list of unsequence TsFiles, this class implements <code>IBatchReader</code> for the
 * TsFiles. Note that an unsequence TsFile can be either closed or unclosed. An unclosed unsequence
 * TsFile consists of data on disk and data in memtables that will be flushed to this unclosed
 * TsFile. This class is used in {@link org.apache.iotdb.db.query.reader.seriesRelated.SeriesReaderWithoutValueFilter}.
 */
public class NewUnseqResourceMergeReader implements IBatchReader {

  private PriorityMergeReader priorityMergeReader = new PriorityMergeReader();
  private List<ChunkMetaData> metaDataList = new ArrayList<>();
  private Filter timeFilter;
  private int index = 0; // used to index current metadata in metaDataList

  private static final int DEFAULT_BATCH_DATA_SIZE = 10000;

  private long nextChunkStartTime = Long.MAX_VALUE;
  private BatchData batchData;
  private TSDataType dataType;

  /**
   * prepare metaDataList
   */
  public NewUnseqResourceMergeReader(Path seriesPath, TSDataType dataType,
      List<TsFileResource> unseqResources, QueryContext context, Filter filter) throws IOException {

    this.dataType = dataType;
    this.timeFilter = filter;
    int priority = 1;

    // get all ChunkMetadata
    for (TsFileResource tsFileResource : unseqResources) {

      // if unseq tsfile is closed or has flushed chunk groups, then endtime map is not empty
      if (!tsFileResource.getEndTimeMap().isEmpty()) {
        if (!ResourceRelatedUtil.isTsFileSatisfied(tsFileResource, timeFilter, seriesPath)) {
          continue;
        }
      }

      /*
       * handle disk chunks of closed or unclosed file
       */
      List<ChunkMetaData> currentChunkMetaDataList;
      if (tsFileResource.isClosed()) {
        // get chunk metadata list of current closed tsfile
        currentChunkMetaDataList = DeviceMetaDataCache.getInstance().get(tsFileResource, seriesPath);
        List<Modification> pathModifications = context
            .getPathModifications(tsFileResource.getModFile(), seriesPath.getFullPath());
        if (!pathModifications.isEmpty()) {
          QueryUtils.modifyChunkMetaData(currentChunkMetaDataList, pathModifications);
        }
      } else {
        // metadata list of already flushed chunk groups
        currentChunkMetaDataList = tsFileResource.getChunkMetaDataList();
      }

      if (!currentChunkMetaDataList.isEmpty()) {
        TsFileSequenceReader tsFileReader = FileReaderManager.getInstance()
            .get(tsFileResource, tsFileResource.isClosed());
        ChunkLoaderImpl chunkLoader = new ChunkLoaderImpl(tsFileReader);

        for (ChunkMetaData chunkMetaData : currentChunkMetaDataList) {
          if (timeFilter != null && !timeFilter.satisfy(chunkMetaData.getStatistics())) {
            currentChunkMetaDataList.remove(chunkMetaData);
            continue;
          }
          chunkMetaData.setPriority(priority++);
          chunkMetaData.setChunkLoader(chunkLoader);
        }
        metaDataList.addAll(currentChunkMetaDataList);
      }

      /*
       * handle mem chunks of unclosed file
       */
      if (!tsFileResource.isClosed()) {
        ChunkReaderWrap memChunkReaderWrap = new ChunkReaderWrap(
            tsFileResource.getReadOnlyMemChunk(), timeFilter);
        priorityMergeReader.addReaderWithPriority(memChunkReaderWrap.getIPointReader(), priority++);
      }
    }

    // sort All ChunkMetadata by start time
    metaDataList = metaDataList.stream().sorted(Comparator.comparing(ChunkMetaData::getStartTime))
        .collect(Collectors.toList());

    // put the first chunk reader into PriorityMergeReader
    if (index < metaDataList.size()) {
      ChunkMetaData metaData = metaDataList.get(index++);
      ChunkReaderWrap diskChunkReader = new ChunkReaderWrap(metaData, metaData.getChunkLoader(), timeFilter);
      priorityMergeReader.addReaderWithPriority(diskChunkReader.getIPointReader(), metaData.getPriority());
    }

    // init next chunk start time
    if (index < metaDataList.size()) {
      nextChunkStartTime = metaDataList.get(index).getStartTime();
    }
  }


  /**
   * Create a ChunkReader with priority for each ChunkMetadata and put the ChunkReader to
   * mergeReader one by one
   */
  @Override public boolean hasNextBatch() throws IOException {
    batchData = new BatchData(dataType, true);

    for (int rowCount = 0; rowCount < DEFAULT_BATCH_DATA_SIZE; rowCount++) {
      if (priorityMergeReader.hasNext()) {

        // current time of priority merge reader >= next chunk start time
        if (priorityMergeReader.current().getTimestamp() >= nextChunkStartTime && index < metaDataList.size()) {
          addNextChunkIntoPriorityMergeReader();
        }

        TimeValuePair timeValuePair = priorityMergeReader.next();
        batchData.putTime(timeValuePair.getTimestamp());
        batchData.putAnObject(timeValuePair.getValue().getValue());

        // largest time of priority merge reader < next chunk start time
        if (!priorityMergeReader.hasNext() && index < metaDataList.size()) {
          addNextChunkIntoPriorityMergeReader();
        }

      } else {
        break;
      }

    }
    return !batchData.isEmpty();
  }

  private void addNextChunkIntoPriorityMergeReader() throws IOException {
    // add next chunk into priority merge reader
    ChunkMetaData metaData = metaDataList.get(index++);
    ChunkReaderWrap diskChunkReader = new ChunkReaderWrap(metaData, metaData.getChunkLoader(), timeFilter);
    priorityMergeReader.addReaderWithPriority(diskChunkReader.getIPointReader(), metaData.getPriority());

    // update next chunk start time
    if (index < metaDataList.size()) {
      nextChunkStartTime = metaDataList.get(index).getStartTime();
    } else {
      nextChunkStartTime = Long.MAX_VALUE;
    }
  }

  @Override
  public BatchData nextBatch() throws IOException {
    return batchData;
  }

  @Override
  public void close() throws IOException {
    priorityMergeReader.close();
  }
}