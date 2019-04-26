/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jd.journalq.network.codec;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jd.journalq.domain.ConsumerPolicy;
import com.jd.journalq.domain.ProducerPolicy;
import com.jd.journalq.domain.TopicType;
import com.jd.journalq.exception.JournalqCode;
import com.jd.journalq.network.command.FetchClusterResponse;
import com.jd.journalq.network.command.JournalqCommandType;
import com.jd.journalq.network.command.Topic;
import com.jd.journalq.network.command.TopicPartition;
import com.jd.journalq.network.command.TopicPartitionGroup;
import com.jd.journalq.network.domain.BrokerNode;
import com.jd.journalq.network.serializer.Serializer;
import com.jd.journalq.network.transport.codec.JournalqHeader;
import com.jd.journalq.network.transport.codec.PayloadCodec;
import com.jd.journalq.network.transport.command.Type;
import io.netty.buffer.ByteBuf;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;

import java.util.Map;
import java.util.Set;

/**
 * FetchClusterResponseCodec
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2018/11/30
 */
public class FetchClusterResponseCodec implements PayloadCodec<JournalqHeader, FetchClusterResponse>, Type {

    private static final byte NONE_TOPIC_TYPE = -1;

    @Override
    public FetchClusterResponse decode(JournalqHeader header, ByteBuf buffer) throws Exception {
        FetchClusterResponse fetchClusterResponse = new FetchClusterResponse();
        Map<String, Topic> topics = Maps.newHashMap();
        Map<Integer, BrokerNode> brokers = Maps.newHashMap();

        short topicSize = buffer.readShort();
        for (int i = 0; i < topicSize; i++) {
            Topic topic = decodeTopic(buffer);
            topics.put(topic.getTopic(), topic);
        }

        short brokerSize = buffer.readShort();
        for (int i = 0; i < brokerSize; i++) {
            BrokerNode brokerNode = decodeBroker(buffer);
            brokers.put(brokerNode.getId(), brokerNode);
        }

        fetchClusterResponse.setTopics(topics);
        fetchClusterResponse.setBrokers(brokers);
        return fetchClusterResponse;
    }

    protected Topic decodeTopic(ByteBuf buffer) throws Exception {
        String topicCode = Serializer.readString(buffer, Serializer.SHORT_SIZE);
        Topic topic = new Topic();
        topic.setTopic(topicCode);

        boolean isExistProducerPolicy = buffer.readBoolean();
        if (isExistProducerPolicy) {
            boolean isNearBy = buffer.readBoolean();
            boolean isSingle = buffer.readBoolean();
            boolean isArchive = buffer.readBoolean();

            short weightSize = buffer.readShort();
            Map<String, Short> weight = Maps.newHashMap();
            for (int i = 0; i < weightSize; i++) {
                weight.put(Serializer.readString(buffer, Serializer.SHORT_SIZE), buffer.readShort());
            }

            short blackListSize = buffer.readShort();
            Set<String> blackList = Sets.newHashSet();
            for (int i = 0; i < blackListSize; i++) {
                blackList.add(Serializer.readString(buffer, Serializer.SHORT_SIZE));
            }

            int timeout = buffer.readInt();
            topic.setProducerPolicy(new ProducerPolicy(isNearBy, isSingle, isArchive, weight, blackList, timeout));
        }

        boolean isExistConsumerPolicy = buffer.readBoolean();
        if (isExistConsumerPolicy) {
            boolean isNearby = buffer.readBoolean();
            boolean isPaused = buffer.readBoolean();
            boolean isArchive = buffer.readBoolean();
            boolean isRetry = buffer.readBoolean();
            boolean isSeq = buffer.readBoolean();
            int ackTimeout = buffer.readInt();
            short batchSize = buffer.readShort();
            int isCurrent = buffer.readInt();
            int delay = buffer.readInt();

            short blackListSize = buffer.readShort();
            Set<String> blackList = Sets.newHashSet();
            for (int i = 0; i < blackListSize; i++) {
                blackList.add(Serializer.readString(buffer, Serializer.SHORT_SIZE));
            }

            int errTimes = buffer.readInt();
            int maxPartitionNum = buffer.readInt();
            int readRetryProbability = buffer.readInt();
            topic.setConsumerPolicy(new ConsumerPolicy(isNearby, isPaused, isArchive, isRetry, isSeq,
                    ackTimeout, batchSize, isCurrent, delay, blackList, errTimes, maxPartitionNum, readRetryProbability,null));
        }

        byte topicType = buffer.readByte();
        if (topicType != NONE_TOPIC_TYPE) {
            topic.setType(TopicType.valueOf(topicType));
        }

        Map<Integer, TopicPartitionGroup> partitionGroups = Maps.newHashMap();
        short partitionGroupSize = buffer.readShort();
        for (int i = 0; i < partitionGroupSize; i++) {
            TopicPartitionGroup partitionGroup = new TopicPartitionGroup();
            Map<Short, TopicPartition> partitions = Maps.newHashMap();
            partitionGroup.setId(buffer.readInt());
            partitionGroup.setLeader(buffer.readInt());
            partitionGroup.setPartitions(partitions);
            partitionGroups.put(partitionGroup.getId(), partitionGroup);

            short partitionSize = buffer.readShort();
            for (int j = 0; j < partitionSize; j++) {
                TopicPartition partition = new TopicPartition();
                partition.setId(buffer.readShort());
                partitions.put(partition.getId(), partition);
            }
        }

        topic.setPartitionGroups(partitionGroups);
        topic.setCode(JournalqCode.valueOf(buffer.readInt()));
        return topic;
    }

    protected BrokerNode decodeBroker(ByteBuf buffer) throws Exception {
        BrokerNode result = new BrokerNode();
        result.setId(buffer.readInt());
        result.setHost(Serializer.readString(buffer, Serializer.SHORT_SIZE));
        result.setPort(buffer.readInt());
        result.setDataCenter(Serializer.readString(buffer, Serializer.SHORT_SIZE));
        result.setNearby(buffer.readBoolean());
        result.setWeight(buffer.readInt());
        return result;
    }

    @Override
    public void encode(FetchClusterResponse payload, ByteBuf buffer) throws Exception {
        buffer.writeShort(payload.getTopics().size());
        for (Map.Entry<String, Topic> entry : payload.getTopics().entrySet()) {
            encodeTopic(entry.getValue(), buffer);
        }

        buffer.writeShort(payload.getBrokers().size());
        for (Map.Entry<Integer, BrokerNode> entry : payload.getBrokers().entrySet()) {
            encodeBroker(entry.getValue(), buffer);
        }
    }

    protected void encodeTopic(Topic topic, ByteBuf buffer) throws Exception {
        ProducerPolicy producerPolicy = topic.getProducerPolicy();
        ConsumerPolicy consumerPolicy = topic.getConsumerPolicy();
        Serializer.write(topic.getTopic(), buffer, Serializer.SHORT_SIZE);

        if (producerPolicy == null) {
            buffer.writeBoolean(false);
        } else {
            buffer.writeBoolean(true);
            buffer.writeBoolean(producerPolicy.getNearby());
            buffer.writeBoolean(producerPolicy.getSingle());
            buffer.writeBoolean(producerPolicy.getArchive());

            if (MapUtils.isEmpty(producerPolicy.getWeight())) {
                buffer.writeShort(0);
            } else {
                buffer.writeShort(producerPolicy.getWeight().size());
                for (Map.Entry<String, Short> entry : producerPolicy.getWeight().entrySet()) {
                    Serializer.write(entry.getKey(), buffer, Serializer.SHORT_SIZE);
                    buffer.writeShort(entry.getValue());
                }
            }

            if (CollectionUtils.isEmpty(producerPolicy.getBlackList())) {
                buffer.writeShort(0);
            } else {
                buffer.writeShort(producerPolicy.getBlackList().size());
                for (String blackList : producerPolicy.getBlackList()) {
                    Serializer.write(blackList, buffer, Serializer.SHORT_SIZE);
                }
            }

            buffer.writeInt(producerPolicy.getTimeOut());
        }

        if (consumerPolicy == null) {
            buffer.writeBoolean(false);
        } else {
            buffer.writeBoolean(true);
            buffer.writeBoolean(consumerPolicy.getNearby());
            buffer.writeBoolean(consumerPolicy.getPaused());
            buffer.writeBoolean(consumerPolicy.getArchive());
            buffer.writeBoolean(consumerPolicy.getRetry());
            buffer.writeBoolean(consumerPolicy.getSeq());
            buffer.writeInt(consumerPolicy.getAckTimeout());
            buffer.writeShort(consumerPolicy.getBatchSize());
            buffer.writeInt(consumerPolicy.getConcurrent());
            buffer.writeInt(consumerPolicy.getDelay());

            if (CollectionUtils.isEmpty(consumerPolicy.getBlackList())) {
                buffer.writeShort(0);
            } else {
                buffer.writeShort(consumerPolicy.getBlackList().size());
                for (String blackList : consumerPolicy.getBlackList()) {
                    Serializer.write(blackList, buffer, Serializer.SHORT_SIZE);
                }
            }

            buffer.writeInt(consumerPolicy.getErrTimes());
            buffer.writeInt(consumerPolicy.getMaxPartitionNum());
            buffer.writeInt(consumerPolicy.getReadRetryProbability());
        }

        if (topic.getType() == null) {
            buffer.writeByte(NONE_TOPIC_TYPE);
        } else {
            buffer.writeByte(topic.getType().code());
        }

        if (MapUtils.isEmpty(topic.getPartitionGroups())) {
            buffer.writeShort(0);
        } else {
            buffer.writeShort(topic.getPartitionGroups().size());
            for (Map.Entry<Integer, TopicPartitionGroup> partitionGroupEntry : topic.getPartitionGroups().entrySet()) {
                TopicPartitionGroup partitionGroup = partitionGroupEntry.getValue();
                buffer.writeInt(partitionGroupEntry.getKey());
                buffer.writeInt(partitionGroup.getLeader());

                buffer.writeShort(partitionGroup.getPartitions().size());
                for (Map.Entry<Short, TopicPartition> partitionEntry : partitionGroup.getPartitions().entrySet()) {
                    buffer.writeShort(partitionEntry.getKey());
                }
            }
        }

        buffer.writeInt(topic.getCode().getCode());
    }

    protected void encodeBroker(BrokerNode brokerNode, ByteBuf buffer) throws Exception {
        buffer.writeInt(brokerNode.getId());
        Serializer.write(brokerNode.getHost(), buffer, Serializer.SHORT_SIZE);
        buffer.writeInt(brokerNode.getPort());
        Serializer.write(brokerNode.getDataCenter(), buffer, Serializer.SHORT_SIZE);
        buffer.writeBoolean(brokerNode.isNearby());
        buffer.writeInt(brokerNode.getWeight());
    }

    @Override
    public int type() {
        return JournalqCommandType.FETCH_CLUSTER_RESPONSE.getCode();
    }
}