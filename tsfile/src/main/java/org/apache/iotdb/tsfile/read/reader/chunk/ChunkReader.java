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

package org.apache.iotdb.tsfile.read.reader.chunk;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.apache.iotdb.tsfile.common.conf.TSFileDescriptor;
import org.apache.iotdb.tsfile.compress.IUnCompressor;
import org.apache.iotdb.tsfile.encoding.common.EndianType;
import org.apache.iotdb.tsfile.encoding.decoder.Decoder;
import org.apache.iotdb.tsfile.file.header.ChunkHeader;
import org.apache.iotdb.tsfile.file.header.PageHeader;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.read.common.BatchData;
import org.apache.iotdb.tsfile.read.common.Chunk;
import org.apache.iotdb.tsfile.read.filter.basic.Filter;
import org.apache.iotdb.tsfile.read.reader.page.PageReader;

public abstract class ChunkReader {

  ChunkHeader chunkHeader;
  private ByteBuffer chunkDataBuffer;

  private IUnCompressor unCompressor;
  private EndianType endianType;
  private Decoder valueDecoder;
  private Decoder timeDecoder = Decoder.getDecoderByType(
      TSEncoding.valueOf(TSFileDescriptor.getInstance().getConfig().getTimeEncoder()),
      TSDataType.INT64);

  private Filter filter;

  private BatchData data;

  private PageHeader pageHeader;
  private boolean hasCachedPageHeader;

  /**
   * Data whose timestamp <= deletedAt should be considered deleted(not be returned).
   */
  protected long deletedAt;

  public ChunkReader(Chunk chunk) {
    this(chunk, null);
  }

  /**
   * constructor of ChunkReader.
   *
   * @param chunk input Chunk object
   * @param filter filter
   */
  public ChunkReader(Chunk chunk, Filter filter) {
    this.filter = filter;
    this.chunkDataBuffer = chunk.getData();
    this.deletedAt = chunk.getDeletedAt();
    this.endianType = chunk.getEndianType();
    chunkHeader = chunk.getHeader();
    this.unCompressor = IUnCompressor.getUnCompressor(chunkHeader.getCompressionType());
    valueDecoder = Decoder
        .getDecoderByType(chunkHeader.getEncodingType(), chunkHeader.getDataType());
    valueDecoder.setEndianType(endianType);
    data = new BatchData(chunkHeader.getDataType());
    hasCachedPageHeader = false;
  }

  /**
   * judge if has nextBatch.
   */
  public boolean hasNextBatch() {
    if (hasCachedPageHeader) {
      return true;
    }
    // construct next satisfied page header
    while (chunkDataBuffer.remaining() > 0) {
      // deserialize a PageHeader from chunkDataBuffer
      pageHeader = PageHeader.deserializeFrom(chunkDataBuffer, chunkHeader.getDataType());

      // if the current page satisfies
      if (pageSatisfied(pageHeader)) {
        hasCachedPageHeader = true;
        return true;
      } else {
        skipBytesInStreamByLength(pageHeader.getCompressedSize());
      }
    }
    return false;
  }

  /**
   * get next data batch.
   *
   * @return next data batch
   * @throws IOException IOException
   */
  public BatchData nextBatch() throws IOException {
    PageReader pageReader = constructPageReaderForNextPage(pageHeader.getCompressedSize());
    hasCachedPageHeader = false;
    if (pageReader.hasNextBatch()) {
      data = pageReader.nextBatch();
      return data;
    }
    return data;
  }

  public BatchData currentBatch() {
    return data;
  }

  public PageHeader nextPageHeader() throws IOException {
    return pageHeader;
  }

  public void skipPageData() {
    skipBytesInStreamByLength(pageHeader.getCompressedSize());
    hasCachedPageHeader = false;
  }

  private void skipBytesInStreamByLength(long length) {
    chunkDataBuffer.position(chunkDataBuffer.position() + (int) length);
  }

  public abstract boolean pageSatisfied(PageHeader pageHeader);

  private PageReader constructPageReaderForNextPage(int compressedPageBodyLength)
      throws IOException {
    byte[] compressedPageBody = new byte[compressedPageBodyLength];

    // already in memory
    if (compressedPageBodyLength > chunkDataBuffer.remaining()) {
      throw new IOException(
          "unexpected byte read length when read compressedPageBody. Expected:"
              + Arrays.toString(compressedPageBody) + ". Actual:" + chunkDataBuffer
              .remaining());
    }
    chunkDataBuffer.get(compressedPageBody, 0, compressedPageBodyLength);
    valueDecoder.reset();
    ByteBuffer pageData = ByteBuffer.wrap(unCompressor.uncompress(compressedPageBody));
    PageReader reader = new PageReader(pageData, chunkHeader.getDataType(),
        valueDecoder, timeDecoder, filter);
    reader.setDeletedAt(deletedAt);
    return reader;
  }

  public void close() {
  }

  public ChunkHeader getChunkHeader() {
    return chunkHeader;
  }
}
