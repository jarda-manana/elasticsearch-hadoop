/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.hadoop.integration.mr;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.elasticsearch.hadoop.cfg.ConfigurationOptions;
import org.elasticsearch.hadoop.integration.HdpBootstrap;
import org.elasticsearch.hadoop.mr.EsOutputFormat;
import org.elasticsearch.hadoop.mr.HadoopCfgUtils;
import org.elasticsearch.hadoop.mr.LinkedMapWritable;
import org.elasticsearch.hadoop.util.RestUtils;
import org.elasticsearch.hadoop.util.WritableUtils;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import static org.junit.Assert.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MRNewApiSaveTest {

    public static class JsonMapper extends Mapper {

        @Override
        protected void map(Object key, Object value, Context context) throws IOException, InterruptedException {
            StringTokenizer st = new StringTokenizer(value.toString(), "\t");
            Map<String, String> entry = new LinkedHashMap<String, String>();

            entry.put("number", st.nextToken());
            entry.put("name", st.nextToken());
            entry.put("url", st.nextToken());
            if (st.hasMoreTokens()) {
                entry.put("picture", st.nextToken());
            }
            context.write(key, WritableUtils.toWritable(entry));
        }
    }

    @Test
    public void testBasicSave() throws Exception {
        Configuration conf = createConf();
        conf.set(ConfigurationOptions.ES_RESOURCE, "mrnewapi/save");

        new Job(conf).waitForCompletion(true);
    }

    @Test
    public void testSaveWithId() throws Exception {
        Configuration conf = createConf();
        conf.set(ConfigurationOptions.ES_RESOURCE, "mrnewapi/savewithid");
        conf.set(ConfigurationOptions.ES_MAPPING_ID, "number");

        new Job(conf).waitForCompletion(true);
    }

    @Test
    public void testCreateWithId() throws Exception {
        Configuration conf = createConf();
        conf.set(ConfigurationOptions.ES_WRITE_OPERATION, "create");
        conf.set(ConfigurationOptions.ES_MAPPING_ID, "number");
        conf.set(ConfigurationOptions.ES_RESOURCE, "mrnewapi/createwithid");

        new Job(conf).waitForCompletion(true);
    }

    @Test
    public void testCreateWithIdShouldFailOnDuplicate() throws Exception {
        Configuration conf = createConf();
        conf.set(ConfigurationOptions.ES_WRITE_OPERATION, "create");
        conf.set(ConfigurationOptions.ES_MAPPING_ID, "number");
        conf.set(ConfigurationOptions.ES_RESOURCE, "mrnewapi/createwithid");

        assertFalse("job should have failed", new Job(conf).waitForCompletion(true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUpdateWithoutId() throws Exception {
        Configuration conf = createConf();
        conf.set(ConfigurationOptions.ES_WRITE_OPERATION, "update");
        conf.set(ConfigurationOptions.ES_RESOURCE, "mrnewapi/update");

        new Job(conf).waitForCompletion(true);
    }

    @Test
    public void testUpdateWithId() throws Exception {
        Configuration conf = createConf();
        conf.set(ConfigurationOptions.ES_WRITE_OPERATION, "update");
        conf.set(ConfigurationOptions.ES_MAPPING_ID, "number");
        conf.set(ConfigurationOptions.ES_RESOURCE, "mrnewapi/update");

        new Job(conf).waitForCompletion(true);
    }

    @Test
    public void testUpdateWithoutUpsert() throws Exception {
        Configuration conf = createConf();
        conf.set(ConfigurationOptions.ES_WRITE_OPERATION, "update");
        conf.set(ConfigurationOptions.ES_MAPPING_ID, "number");
        conf.set(ConfigurationOptions.ES_RESOURCE, "mrnewapi/updatewoupsert");
        conf.set(ConfigurationOptions.ES_UPSERT_DOC, "false");

        assertFalse("job should have failed", new Job(conf).waitForCompletion(true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexAutoCreateDisabled() throws Exception {
        Configuration conf = createConf();
        conf.set(ConfigurationOptions.ES_RESOURCE, "mrnewapi/non-existing");
        conf.set(ConfigurationOptions.ES_INDEX_AUTO_CREATE, "no");

        new Job(conf).waitForCompletion(true);
    }

    @Test
    public void testParentChild() throws Exception {
        Configuration conf = createConf();
        conf.set(ConfigurationOptions.ES_RESOURCE, "mrnewapi/child");
        conf.set(ConfigurationOptions.ES_INDEX_AUTO_CREATE, "no");
        conf.set(ConfigurationOptions.ES_MAPPING_PARENT, "number");

        RestUtils.putMapping("mrnewapi/child", "org/elasticsearch/hadoop/integration/mr-child.json");

        new Job(conf).waitForCompletion(true);
    }

    private Configuration createConf() throws IOException {
        Configuration conf = HdpBootstrap.hadoopConfig();
        HadoopCfgUtils.setGenericOptions(conf);

        Job job = new Job(conf);
        job.setInputFormatClass(TextInputFormat.class);
        job.setOutputFormatClass(EsOutputFormat.class);
        job.setMapOutputValueClass(LinkedMapWritable.class);
        job.setMapperClass(JsonMapper.class);
        job.setNumReduceTasks(0);

        TextInputFormat.addInputPath(job, new Path("src/test/resources/artists.dat"));
        File fl = new File("src/test/resources/artists.dat");
        long splitSize = fl.length() / 3;
        TextInputFormat.setMaxInputSplitSize(job, splitSize);
        TextInputFormat.setMinInputSplitSize(job, 50);
        return job.getConfiguration();
    }
}