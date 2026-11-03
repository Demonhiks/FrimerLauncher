import com.frimer.launcher.core.friends.Mqtt;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Проверка MQTT-клиента на публичном брокере: connect, subscribe, publish. */
public final class TestMqtt {

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "broker.hivemq.com";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 1883;

        String topic = "frimer/test/" + System.nanoTime();
        CountDownLatch received = new CountDownLatch(1);
        StringBuilder got = new StringBuilder();

        Mqtt listener = new Mqtt(host, port, "frimer-test-listen-" + System.nanoTime(),
                new Mqtt.Listener() {
                    @Override
                    public void onMessage(String t, byte[] payload) {
                        if (topic.equals(t)) {
                            got.append(new String(payload, StandardCharsets.UTF_8));
                            received.countDown();
                        }
                    }

                    @Override
                    public void onDisconnected(Throwable cause) {
                        System.out.println("  отключились: " + cause);
                    }
                });
        listener.connect(10000);
        listener.subscribe(topic);
        System.out.println("подписались на " + topic);

        Mqtt sender = new Mqtt(host, port, "frimer-test-send-" + System.nanoTime(), null);
        sender.connect(10000);
        sender.publish(topic, "привет из теста".getBytes(StandardCharsets.UTF_8));

        boolean ok = received.await(10, TimeUnit.SECONDS);
        System.out.println(ok ? "ПРИШЛО: " + got : "НЕ ПРИШЛО за 10 секунд");
        sender.close();
        listener.close();
        if (!ok) {
            System.exit(1);
        }
    }
}
