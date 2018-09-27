/*
 * Copyright © 2017 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.hydrator.plugin.batch.source;

import co.cask.cdap.api.data.format.StructuredRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.input.CombineFileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.CombineFileRecordReader;
import org.apache.hadoop.mapreduce.lib.input.CombineFileRecordReaderWrapper;
import org.apache.hadoop.mapreduce.lib.input.CombineFileSplit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Similar to CombineTextInputFormat except it uses PathTrackingInputFormat to keep track of filepaths that
 * records were read from.
 */
public class CombinePathTrackingInputFormat extends CombineFileInputFormat<NullWritable, StructuredRecord> {
  public static final String HEADER = "combine.path.tracking.header";

  @Override
  public List<InputSplit> getSplits(JobContext job) throws IOException {
    List<InputSplit> fileSplits = super.getSplits(job);
    Configuration hConf = job.getConfiguration();

    boolean copyHeader = hConf.getBoolean(PathTrackingInputFormat.COPY_HEADER, false);
    List<InputSplit> splits = new ArrayList<>(fileSplits.size());

    if (!copyHeader) {
      for (InputSplit split : fileSplits) {
        splits.add(new CombineHeaderFileSplit((CombineFileSplit) split, null));
      }
      return splits;
    }

    String header = null;
    for (InputSplit split : fileSplits) {
      CombineFileSplit combineFileSplit = (CombineFileSplit) split;

      Path[] paths = combineFileSplit.getPaths();
      // read the header from one of the files if the header hasn't been determined yet
      // this assumes that every file has the same header
      if (header == null) {
        for (Path path : paths) {
          header = getHeader(path.getFileSystem(hConf), path);
          if (header != null) {
            break;
          }
        }
      }
      splits.add(new CombineHeaderFileSplit(combineFileSplit, header));
    }

    return splits;
  }

  @Nullable
  private String getHeader(FileSystem fs, Path path) throws IOException {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(fs.open(path), StandardCharsets.UTF_8))) {
      return reader.readLine();
    }
  }

  @Override
  public RecordReader<NullWritable, StructuredRecord> createRecordReader(InputSplit split, TaskAttemptContext context)
    throws IOException {
    CombineHeaderFileSplit combineSplit = (CombineHeaderFileSplit) split;
    if (combineSplit.getHeader() != null) {
      context.getConfiguration().set(HEADER, combineSplit.getHeader());
    }
    return new CombineFileRecordReader<>(combineSplit, context, RecordReaderWrapper.class);
  }

  /**
   * This is just a wrapper that's responsible for delegating to a corresponding RecordReader in
   * {@link PathTrackingInputFormat}. All it does is pick the i'th path in the CombineFileSplit to create a
   * FileSplit and use the delegate RecordReader to read that split.
   */
  private static class RecordReaderWrapper extends CombineFileRecordReaderWrapper<NullWritable, StructuredRecord> {

    // this constructor signature is required by CombineFileRecordReader
    RecordReaderWrapper(CombineFileSplit split, TaskAttemptContext context,
                        Integer idx) throws IOException, InterruptedException {
      super(new PathTrackingInputFormat(), split, context, idx);
    }
  }
}
