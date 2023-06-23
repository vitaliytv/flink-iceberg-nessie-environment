package com.dremio_developer_advocacy;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.types.Row;
import org.apache.iceberg.*;
import org.apache.iceberg.flink.sink.FlinkSink;
import org.apache.iceberg.flink.TableLoader;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.nessie.NessieCatalog;
import org.apache.iceberg.hadoop.HadoopCatalog;
import org.apache.hadoop.conf.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class IcebergFlinkJob {

    public static void main(String[] args) throws Exception {
        

        // Create Flink Execution Environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Create Nessie Catalog Settings Map
        Map<String, String> nessieConfig = new HashMap<>();
        // Add settings to the map
        nessieConfig.put("uri", "http://localhost:19120/api/v1");
        nessieConfig.put("ref", "main");
        nessieConfig.put("auth", "none");
        nessieConfig.put("io-impl", "org.apache.iceberg.aws.s3.S3FileIO");
        nessieConfig.put("warehouse", "s3://warehouse/wh/");
        nessieConfig.put("s3.endpoint", "http://minio:9000");

        // Create Nessie Catalog
        Catalog nessieCatalog = new NessieCatalog();
        // Initialize the Nessie Catalog
        nessieCatalog.initialize("nessie", nessieConfig);

        // Create Iceberg Schema
        Schema schema = new Schema(
                org.apache.iceberg.types.Types.NestedField.optional(
                        1, "id",
                        org.apache.iceberg.types.Types.IntegerType.get()),
                org.apache.iceberg.types.Types.NestedField.optional(
                        2, "name",
                        org.apache.iceberg.types.Types.StringType.get()),
                org.apache.iceberg.types.Types.NestedField.optional(
                        3, "age",
                        org.apache.iceberg.types.Types.IntegerType.get()));

        // Create Iceberg Partition Spec
        PartitionSpec spec = PartitionSpec.builderFor(schema).identity("age").build();

        TableIdentifier id = TableIdentifier.of("default", "mytable");

        if (!nessieCatalog.tableExists(id)) {
            nessieCatalog.createTable(id, schema, spec, nessieConfig);
        }

        DataStream<Row> dataStream = env.addSource(new RandomDataGeneratorSource())
                .returns(Types.ROW(Types.INT, Types.STRING, Types.INT));

        FlinkSink.forRowData(dataStream)
                .tableLoader(TableLoader.fromCatalog(nessieCatalog, id))
                .overwrite(false)
                .build();

        env.execute("Iceberg Flink Job");
    }

    public static class RandomDataGeneratorSource implements SourceFunction<Row> {
        private boolean running = true;

        @Override
        public void run(SourceContext<Row> ctx) throws Exception {
            Random rand = new Random();
            int count = 0;
            while (running && count < 5) {
                Row row = new Row(3);
                row.setField(0, rand.nextInt());
                row.setField(1, UUID.randomUUID().toString());
                row.setField(2, rand.nextInt(100));
                ctx.collect(row);
                count++;
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }
}
