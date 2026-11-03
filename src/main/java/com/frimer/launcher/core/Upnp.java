package com.frimer.launcher.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Проброс порта через UPnP IGD: лаунчер сам просит роутер открыть порт,
 * чтобы друзья могли подключиться по внешнему IP. Без аккаунтов и сторонних сервисов.
 *
 * Работает, если роутер поддерживает UPnP (большинство домашних) и провайдер
 * не прячет вас за CGNAT. Иначе — честный отказ и фолбэк на playit.gg.
 */
public final class Upnp {

    private Upnp() {
    }

    private static final String SSDP_ADDRESS = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private static final String[] SERVICE_TYPES = {
            "urn:schemas-upnp-org:service:WANIPConnection:1",
            "urn:schemas-upnp-org:service:WANPPPConnection:1",
            "urn:schemas-upnp-org:service:WANIPConnection:2",
    };

    /** Результат проброса: внешний адрес и порт. */
    public record Mapping(String externalIp, int externalPort, int internalPort, String router) {
        public String address() {
            return externalIp + ":" + externalPort;
        }
    }

    /** Служба роутера, через которую делаем проброс. */
    private record Gateway(String controlUrl, String serviceType, String name) {
    }

    /**
     * Открыть порт на роутере.
     *
     * @param internalPort локальный порт сервера
     * @param description  подпись правила в роутере
     * @param leaseSeconds срок жизни правила (0 — бессрочно)
     */
    public static Mapping open(int internalPort, String description, int leaseSeconds)
            throws IOException {
        Gateway gateway = discover();
        if (gateway == null) {
            throw new IOException("Роутер не ответил на UPnP-запрос (возможно, UPnP выключен)");
        }
        String localIp = localAddress();
        if (localIp == null) {
            throw new IOException("Не удалось определить локальный IP");
        }
        String externalIp = externalAddress(gateway);
        addMapping(gateway, internalPort, localIp, description, leaseSeconds);
        return new Mapping(externalIp == null || externalIp.isBlank() ? "?" : externalIp,
                internalPort, internalPort, gateway.name());
    }

    /** Удалить правило (best-effort, вызывать при остановке сервера). */
    public static void close(int externalPort) {
        try {
            Gateway gateway = discover();
            if (gateway == null) {
                return;
            }
            String body = envelope(gateway.serviceType(), "DeletePortMapping",
                    "<NewRemoteHost></NewRemoteHost>"
                            + "<NewProtocol>TCP</NewProtocol>"
                            + "<NewExternalPort>" + externalPort + "</NewExternalPort>");
            soap(gateway.controlUrl(), gateway.serviceType(), "DeletePortMapping", body);
        } catch (Exception ignored) {
            // правило истечёт само
        }
    }

    // ------------------------------------------------------------------ SSDP

    private static Gateway discover() {
        List<String> locations = new ArrayList<>();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(700);
            String request = "M-SEARCH * HTTP/1.1\r\n"
                    + "HOST: " + SSDP_ADDRESS + ":" + SSDP_PORT + "\r\n"
                    + "MAN: \"ssdp:discover\"\r\n"
                    + "MX: 2\r\n"
                    + "ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n";
            byte[] data = request.getBytes(StandardCharsets.US_ASCII);
            InetAddress group = InetAddress.getByName(SSDP_ADDRESS);
            for (int attempt = 0; attempt < 2; attempt++) {
                socket.send(new DatagramPacket(data, data.length, group, SSDP_PORT));
                long deadline = System.currentTimeMillis() + 1200;
                while (System.currentTimeMillis() < deadline) {
                    try {
                        byte[] buffer = new byte[4096];
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        String text = new String(packet.getData(), 0, packet.getLength(),
                                StandardCharsets.US_ASCII);
                        for (String line : text.split("\r\n")) {
                            if (line.toLowerCase(Locale.ROOT).startsWith("location:")) {
                                String location = line.substring(9).trim();
                                if (!locations.contains(location)) {
                                    locations.add(location);
                                }
                            }
                        }
                    } catch (java.net.SocketTimeoutException e) {
                        break;
                    }
                }
                if (!locations.isEmpty()) {
                    break;
                }
            }
        } catch (IOException e) {
            return null;
        }

        for (String location : locations) {
            Gateway gateway = fromDescription(location);
            if (gateway != null) {
                return gateway;
            }
        }
        return null;
    }

    /** Разобрать описание устройства и найти службу WAN*-Connection. */
    private static Gateway fromDescription(String location) {
        try {
            String xml = fetch(location);
            if (xml == null) {
                return null;
            }
            Document document = parse(xml);
            String friendlyName = textOf(document, "friendlyName");
            URI base = URI.create(location);
            for (String serviceType : SERVICE_TYPES) {
                NodeList nodes = document.getElementsByTagName("service");
                for (int i = 0; i < nodes.getLength(); i++) {
                    Element service = (Element) nodes.item(i);
                    String type = childText(service, "serviceType");
                    String control = childText(service, "controlURL");
                    if (type == null || control == null || !type.contains(serviceType)) {
                        continue;
                    }
                    URI controlUri = base.resolve(control);
                    if ("http".equalsIgnoreCase(controlUri.getScheme())) {
                        return new Gateway(controlUri.toString(), serviceType,
                                friendlyName == null ? "роутер" : friendlyName);
                    }
                }
            }
        } catch (Exception ignored) {
            // пробуем следующее устройство
        }
        return null;
    }

    private static String textOf(Document document, String tag) {
        NodeList nodes = document.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    private static String childText(Element parent, String tag) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && tag.equals(element.getTagName())) {
                String text = element.getTextContent();
                return text == null ? null : text.trim();
            }
        }
        return null;
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setNamespaceAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    // ------------------------------------------------------------------ SOAP

    private static void addMapping(Gateway gateway, int port, String localIp, String description,
                                   int leaseSeconds) throws IOException {
        String body = envelope(gateway.serviceType(), "AddPortMapping",
                "<NewRemoteHost></NewRemoteHost>"
                        + "<NewExternalPort>" + port + "</NewExternalPort>"
                        + "<NewProtocol>TCP</NewProtocol>"
                        + "<NewInternalPort>" + port + "</NewInternalPort>"
                        + "<NewInternalClient>" + localIp + "</NewInternalClient>"
                        + "<NewEnabled>1</NewEnabled>"
                        + "<NewPortMappingDescription>" + escape(description)
                        + "</NewPortMappingDescription>"
                        + "<NewLeaseDuration>" + Math.max(0, leaseSeconds) + "</NewLeaseDuration>");
        try {
            soap(gateway.controlUrl(), gateway.serviceType(), "AddPortMapping", body);
        } catch (IOException e) {
            // 718 = правило уже есть, это не ошибка
            if (e.getMessage() != null && e.getMessage().contains("718")) {
                return;
            }
            throw e;
        }
    }

    private static String externalAddress(Gateway gateway) {
        try {
            String body = envelope(gateway.serviceType(), "GetExternalIPAddress", "");
            String response = soap(gateway.controlUrl(), gateway.serviceType(),
                    "GetExternalIPAddress", body);
            String marker = "NewExternalIPAddress";
            int start = response.indexOf("<" + marker + ">");
            if (start < 0) {
                start = response.indexOf("<" + marker + " ");
            }
            if (start < 0) {
                return null;
            }
            int open = response.indexOf('>', start);
            int close = response.indexOf("</", open);
            if (open < 0 || close < 0) {
                return null;
            }
            return response.substring(open + 1, close).trim();
        } catch (IOException e) {
            return null;
        }
    }

    private static String envelope(String serviceType, String action, String arguments) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\""
                + " s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
                + "<s:Body><u:" + action + " xmlns:u=\"" + serviceType + "\">"
                + arguments
                + "</u:" + action + "></s:Body></s:Envelope>";
    }

    private static String soap(String controlUrl, String serviceType, String action, String body)
            throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(controlUrl).toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(4000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
        connection.setRequestProperty("SOAPAction", "\"" + serviceType + "#" + action + "\"");
        connection.setRequestProperty("User-Agent", Http.UA);
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        try (var out = connection.getOutputStream()) {
            out.write(payload);
        }
        int code = connection.getResponseCode();
        String text = readAll(code >= 400 ? connection.getErrorStream() : connection.getInputStream());
        if (code >= 400) {
            throw new IOException("UPnP ответил HTTP " + code + ": " + Utils.shortText(text, 160));
        }
        return text;
    }

    private static String fetch(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestProperty("User-Agent", Http.UA);
            if (connection.getResponseCode() != 200) {
                return null;
            }
            return readAll(connection.getInputStream());
        } catch (IOException e) {
            return null;
        }
    }

    private static String readAll(java.io.InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Локальный IPv4-адрес, который видит роутер. */
    public static String localAddress() {
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) {
                    continue;
                }
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof java.net.Inet4Address && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // сеть недоступна
        }
        return null;
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Быстрая проверка: есть ли вообще UPnP-роутер (для подсказки в UI). */
    public static boolean available() {
        try {
            return discover() != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Имя из описания устройства (для отладки). */
    public static String routerName() {
        Gateway gateway = discover();
        return gateway == null ? "" : gateway.name();
    }

    /** Никогда не бросает: удобно для println-проверок. */
    public static String safeAddress(int port) {
        try {
            return open(port, "Frimer", 3600).address();
        } catch (IOException e) {
            return null;
        }
    }

    /** Случайный токен для уникальной подписи правила. */
    static String token() {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(
                    BigInteger.valueOf(System.nanoTime()).toByteArray());
            return new BigInteger(1, digest).toString(16).substring(0, 8);
        } catch (Exception e) {
            return "frimer";
        }
    }
}
