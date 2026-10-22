package com.watchsuite.core;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 极简局域网 HTTP 服务器：手机浏览器直接上传 / 下载手表文件。 */
public final class HttpShare {

    public static final String DIR = "/storage/emulated/0/Download/腕上快传";

    public interface Ui {
        void onChanged();
    }

    private static ServerSocket server;
    private static final ExecutorService WORK = Executors.newCachedThreadPool();
    public static int uploads = 0;
    public static long uploadedBytes = 0;
    public static Ui ui;

    private HttpShare() {}

    public static boolean running() {
        return server != null && !server.isClosed();
    }

    public static int port() {
        return server == null ? 0 : server.getLocalPort();
    }

    public static synchronized void start() throws IOException {
        if (running()) return;
        for (int p = 8080; p <= 8090; p++) {
            try {
                server = new ServerSocket(p, 16);
                break;
            } catch (IOException e) {
                if (p == 8090) throw e;
            }
        }
        new File(DIR).mkdirs();
        final ServerSocket ss = server;
        Thread t = new Thread(() -> {
            while (!ss.isClosed()) {
                try {
                    Socket s = ss.accept();
                    WORK.execute(() -> handle(s));
                } catch (Throwable e) {
                    break;
                }
            }
        }, "http-share");
        t.setDaemon(true);
        t.start();
        notifyUi();
    }

    public static synchronized void stop() {
        try {
            if (server != null) server.close();
        } catch (Throwable ignored) {
        }
        server = null;
        notifyUi();
    }

    private static void notifyUi() {
        if (ui != null) ui.onChanged();
    }

    // ---------- 请求处理 ----------

    private static void handle(Socket s) {
        try {
            s.setSoTimeout(15000);
            InputStream in = s.getInputStream();
            OutputStream out = new BufferedOutputStream(s.getOutputStream(), 32 * 1024);

            String reqLine = readLine(in);
            if (reqLine == null || reqLine.isEmpty()) return;
            String[] parts = reqLine.split(" ");
            String method = parts[0];
            String target = parts.length > 1 ? parts[1] : "/";

            long contentLen = 0;
            String contentType = "";
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                String low = line.toLowerCase(Locale.US);
                if (low.startsWith("content-length:")) {
                    contentLen = Long.parseLong(line.substring(15).trim());
                } else if (low.startsWith("content-type:")) {
                    contentType = line.substring(13).trim();
                }
            }

            if ("GET".equals(method)) {
                if (target.startsWith("/f?")) {
                    String n = param(target, "n");
                    serveFile(out, n);
                } else {
                    index(out);
                }
            } else if ("POST".equals(method)) {
                if (target.startsWith("/up")) {
                    upload(in, out, contentType, contentLen);
                } else {
                    index(out);
                }
            }
            out.flush();
            s.close();
        } catch (Throwable ignored) {
        }
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder b = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') b.append((char) c);
        }
        return b.toString();
    }

    private static String param(String target, String key) throws IOException {
        int i = target.indexOf('?');
        if (i < 0) return null;
        for (String kv : target.substring(i + 1).split("&")) {
            int e = kv.indexOf('=');
            if (e > 0 && kv.substring(0, e).equals(key)) {
                return URLDecoder.decode(kv.substring(e + 1), "UTF-8");
            }
        }
        return null;
    }

    private static void index(OutputStream out) throws IOException {
        StringBuilder rows = new StringBuilder();
        File dir = new File(DIR);
        File[] fs = dir.listFiles();
        if (fs != null) {
            for (File f : fs) {
                rows.append("<li><a href=\"/f?n=").append(urlEnc(f.getName()))
                        .append("\">").append(esc(f.getName()))
                        .append("</a><span>").append(Fs.human(f.length())).append("</span></li>");
            }
        }
        String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>腕上快传</title><style>"
                + "body{font-family:sans-serif;background:#10151c;color:#eef4fa;margin:0;padding:16px}"
                + "h2{margin:6px 0 12px}form{background:#1a2230;border-radius:14px;padding:14px;margin-bottom:14px}"
                + "input[type=file]{color:#9fb4c9;margin-bottom:10px;display:block}"
                + "button{background:#4da3ff;color:#06121e;border:0;border-radius:10px;padding:10px 22px;font-size:16px}"
                + "ul{list-style:none;padding:0;margin:0}"
                + "li{display:flex;justify-content:space-between;background:#1a2230;border-radius:10px;padding:12px;margin-bottom:8px}"
                + "a{color:#8ec5ff;text-decoration:none;word-break:break-all}span{color:#8ca0b4;white-space:nowrap;margin-left:10px}"
                + "</style></head><body>"
                + "<h2>⌚ 腕上快传</h2>"
                + "<form action=\"/up\" method=\"post\" enctype=\"multipart/form-data\">"
                + "<input type=\"file\" name=\"f\" multiple required>"
                + "<button>上传到手表</button></form>"
                + "<ul>" + rows + "</ul>"
                + "<p style=\"color:#5c6e80;font-size:13px\">文件保存在手表 Download/腕上快传</p>"
                + "</body></html>";
        byte[] data = html.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n"
                + "Content-Length: " + data.length + "\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(data);
    }

    private static void serveFile(OutputStream out, String name) throws IOException {
        if (name == null || name.contains("/") || name.contains("\\") || name.contains("..")) {
            out.write("HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n".getBytes());
            return;
        }
        File f = new File(DIR, name);
        if (!f.exists()) {
            out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".getBytes());
            return;
        }
        String mime = Fs.mime(name);
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: " + mime + "\r\nContent-Length: "
                + f.length() + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        try (FileInputStream fin = new FileInputStream(f)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = fin.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private static void upload(InputStream in, OutputStream out, String contentType,
                               long contentLen) throws IOException {
        new File(DIR).mkdirs();
        String boundary = null;
        if (contentType != null && contentType.contains("boundary=")) {
            boundary = contentType.substring(contentType.indexOf("boundary=") + 9);
            if (boundary.startsWith("\"")) boundary = boundary.replace("\"", "");
        }
        if (boundary == null || contentLen <= 0) {
            out.write("HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n".getBytes());
            return;
        }
        byte[] body = readFully(in, contentLen);
        ArrayList<String> names = new ArrayList<>();
        byte[] bd = ("--" + boundary).getBytes(StandardCharsets.UTF_8);

        int pos = indexOf(body, bd, 0);
        while (pos >= 0) {
            int partStart = pos + bd.length;
            if (partStart + 1 < body.length && body[partStart] == '-' && body[partStart + 1] == '-') break;
            while (partStart < body.length && (body[partStart] == '\r' || body[partStart] == '\n')) partStart++;
            // 头部
            int headEnd = indexOf(body, "\r\n\r\n".getBytes(StandardCharsets.UTF_8), partStart);
            if (headEnd < 0) break;
            String head = new String(body, partStart, headEnd - partStart, StandardCharsets.UTF_8);
            int dataStart = headEnd + 4;
            int next = indexOf(body, bd, dataStart);
            if (next < 0) break;
            int dataEnd = next - 2; // 去掉前面的 \r\n
            String fname = null;
            for (String hl : head.split("\r\n")) {
                if (hl.toLowerCase(Locale.US).startsWith("content-disposition")) {
                    for (String seg : hl.split(";")) {
                        seg = seg.trim();
                        if (seg.startsWith("filename=")) {
                            fname = seg.substring(9).replace("\"", "");
                        }
                    }
                }
            }
            if (fname != null && !fname.isEmpty() && dataEnd > dataStart) {
                String safe = fname.replace("/", "_").replace("\\", "_");
                File dst = dedupe(new File(DIR, safe));
                try (FileOutputStream fo = new FileOutputStream(dst)) {
                    fo.write(body, dataStart, dataEnd - dataStart);
                }
                names.add(dst.getName());
                uploads++;
                uploadedBytes += dataEnd - dataStart;
            }
            pos = next;
        }
        notifyUi();
        String msg = names.isEmpty() ? "没有收到文件" : "已接收 " + names.size() + " 个文件";
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: "
                + data.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(data);
    }

    private static File dedupe(File f) {
        if (!f.exists()) return f;
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        String base = dot > 0 ? n.substring(0, dot) : n;
        String ext = dot > 0 ? n.substring(dot) : "";
        for (int i = 2; i < 999; i++) {
            File t = new File(f.getParentFile(), base + "(" + i + ")" + ext);
            if (!t.exists()) return t;
        }
        return new File(f.getParentFile(), System.currentTimeMillis() + "_" + n);
    }

    private static byte[] readFully(InputStream in, long len) throws IOException {
        byte[] buf = new byte[(int) len];
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) break;
            off += n;
        }
        return buf;
    }

    private static int indexOf(byte[] hay, byte[] needle, int from) {
        outer:
        for (int i = Math.max(0, from); i <= hay.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String urlEnc(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
