/*
 * Copyright © 2015 Cask Data, Inc.
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

import co.cask.cdap.api.annotation.Description;
import co.cask.cdap.api.annotation.Name;
import co.cask.cdap.api.annotation.Plugin;
import co.cask.cdap.api.annotation.Requirements;
import co.cask.cdap.api.data.format.StructuredRecord;
import co.cask.cdap.api.dataset.lib.FileSetProperties;
import co.cask.cdap.api.dataset.lib.KeyValue;
import co.cask.cdap.api.dataset.lib.PartitionedFileSet;
import co.cask.cdap.etl.api.Emitter;
import co.cask.cdap.etl.api.PipelineConfigurer;
import co.cask.hydrator.plugin.common.AvroToStructuredTransformer;
import co.cask.hydrator.plugin.common.FileSetUtil;
import co.cask.hydrator.plugin.common.SnapshotFileSetConfig;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.mapred.AvroKey;
import org.apache.hadoop.io.NullWritable;

import javax.annotation.Nullable;

/**
 * Reads data written by a {@link SnapshotFileBatchAvroSource}. Reads only the most recent partition.
 */
@Plugin(type = "batchsource")
@Name("SnapshotAvro")
@Description("Reads the most recent snapshot that was written to a SnapshotAvro sink.")
@Requirements(datasetTypes = PartitionedFileSet.TYPE)
public class SnapshotFileBatchAvroSource extends SnapshotFileBatchSource<AvroKey<GenericRecord>, NullWritable> {
  private final AvroToStructuredTransformer recordTransformer = new AvroToStructuredTransformer();
  private final SnapshotAvroConfig config;

  public SnapshotFileBatchAvroSource(SnapshotAvroConfig config) {
    super(config);
    this.config = config;
  }

  @Override
  public void configurePipeline(PipelineConfigurer pipelineConfigurer) {
    super.configurePipeline(pipelineConfigurer);
    Preconditions.checkArgument(!Strings.isNullOrEmpty(config.schema), "Schema must be specified.");
    try {
      co.cask.cdap.api.data.schema.Schema schema = co.cask.cdap.api.data.schema.Schema.parseJson(config.schema);
      pipelineConfigurer.getStageConfigurer().setOutputSchema(schema);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid output schema: " + e.getMessage(), e);
    }
  }

  @Override
  public void transform(KeyValue<AvroKey<GenericRecord>, NullWritable> input,
                        Emitter<StructuredRecord> emitter) throws Exception {
    emitter.emit(recordTransformer.transform(input.getKey().datum()));
  }

  @Override
  protected void addFileProperties(FileSetProperties.Builder propertiesBuilder) {
    FileSetUtil.configureAvroFileSet(config.schema, propertiesBuilder);
  }

  /**
   * Config for SnapshotFileBatchAvroSource
   */
  public static class SnapshotAvroConfig extends SnapshotFileSetConfig {
    @Description("The Avro schema of the records to read.")
    private String schema;

    public SnapshotAvroConfig(String name, @Nullable String basePath, String schema) {
      super(name, basePath, null);
      this.schema = schema;
    }
  }
}
