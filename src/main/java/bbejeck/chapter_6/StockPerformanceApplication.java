package bbejeck.chapter_6;


import bbejeck.chapter_6.processor.MapValueProcessor;
import bbejeck.chapter_6.processor.PrintingProcessorSupplier;
import bbejeck.chapter_6.processor.StockPerformanceProcessor;
import bbejeck.clients.producer.MockDataProducer;
import bbejeck.model.Purchase;
import bbejeck.model.PurchasePattern;
import bbejeck.model.RewardAccumulator;
import bbejeck.model.StockPerformance;
import bbejeck.model.StockTransaction;
import bbejeck.util.serde.StreamsSerdes;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.processor.TopologyBuilder;
import org.apache.kafka.streams.processor.WallclockTimestampExtractor;
import org.apache.kafka.streams.state.Stores;

import java.util.Properties;

import static org.apache.kafka.streams.processor.TopologyBuilder.AutoOffsetReset.EARLIEST;
import static org.apache.kafka.streams.processor.TopologyBuilder.AutoOffsetReset.LATEST;

public class StockPerformanceApplication {


    public static void main(String[] args) throws Exception {
        MockDataProducer.produceStockTransactions(25,25, 25);


        StreamsConfig streamsConfig = new StreamsConfig(getProperties());
        Deserializer<String> stringDeserializer = Serdes.String().deserializer();
        Serializer<String> stringSerializer = Serdes.String().serializer();
        Serde<StockPerformance> stockPerformanceSerde = StreamsSerdes.StockPerformanceSerde();
        Deserializer<StockPerformance> stockPerformanceDeserializer = stockPerformanceSerde.deserializer();
        Serializer<StockPerformance> stockPerformanceSerializer = stockPerformanceSerde.serializer();
        Serde<StockTransaction> stockTransactionSerde = StreamsSerdes.StockTransactionSerde();
        Deserializer<StockTransaction> stockTransactionDeserializer = stockTransactionSerde.deserializer();


        TopologyBuilder builder = new TopologyBuilder();
        String stocksStateStore = "stock-performance-store";
        double differentialThreshold = 0.02;

        StockPerformanceProcessor stockPerformanceProcessor = new StockPerformanceProcessor(stocksStateStore, differentialThreshold);

        builder.addSource(LATEST,"stocks-source", stringDeserializer, stockTransactionDeserializer, "stock-transactions")
                .addProcessor("stocks-processor", () -> stockPerformanceProcessor, "stocks-source")
                .addStateStore(Stores.create("stock-transactions").withStringKeys()
                        .withValues(stockPerformanceSerde).inMemory().maxEntries(100).build(),"stocks-processor")
                .addSink("stocks-sink", "stock-performance", stringSerializer, stockPerformanceSerializer, "stocks-processor");


        builder.addProcessor("stocks-printer", new PrintingProcessorSupplier("purchase"), "stocks-processor");

        KafkaStreams kafkaStreams = new KafkaStreams(builder, streamsConfig);
        System.out.println("Stock Analysis App Started");
        kafkaStreams.start();
        Thread.sleep(70000);
        System.out.println("Shutting down the Stock Analysis App now");
        kafkaStreams.close();
        MockDataProducer.shutdown();
    }

    private static Properties getProperties() {
        Properties props = new Properties();
        props.put(StreamsConfig.CLIENT_ID_CONFIG, "stock-analysis-client");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "stock-analysis--group");
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "stock-analysis--appid");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, 1);
        props.put(StreamsConfig.KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.TIMESTAMP_EXTRACTOR_CLASS_CONFIG, WallclockTimestampExtractor.class);
        return props;
    }
}
