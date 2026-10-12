package com.lsj.demo.manager.uplode;

import com.lsj.demo.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * URL 上传的校验与文件名解析测试
 * <p>
 * 校验逻辑全部在调用对象存储之前完成，因此这里不需要真实的 COS 环境，
 * 起一个本地 HttpServer 当作图源即可覆盖 URL 的各种边界。
 */
class UrlPictureUploadTest {

    private static HttpServer server;
    private static String baseUrl;

    /** 一张极小但合法的 PNG（1x1，纯色） */
    private static final byte[] TINY_PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53,
            (byte) 0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
            0x54, 0x08, (byte) 0xD7, 0x63, 0x60, 0x00, 0x00, 0x00,
            0x02, 0x00, 0x01, (byte) 0xE2, 0x21, 0x71, (byte) 0x8B,
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
            (byte) 0xAE, 0x42, 0x60, (byte) 0x82,
    };

    private final UrlPictureUpload urlPictureUpload = new UrlPictureUpload();

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        // 正常图片
        server.createContext("/good.png", exchange -> respond(exchange, 200, "image/png", TINY_PNG));
        // 非图片类型
        server.createContext("/text.txt", exchange ->
                respond(exchange, 200, "text/plain", "not an image".getBytes(StandardCharsets.UTF_8)));
        // 后缀伪装成 png，但服务端返回 text/plain，用于验证 Content-Type 校验
        server.createContext("/fake.png", exchange ->
                respond(exchange, 200, "text/plain", "not an image".getBytes(StandardCharsets.UTF_8)));
        // 声明 3MB，触发体积校验（注意：这里只校验响应头，不需要真的发 3MB）
        server.createContext("/big.png", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.getResponseHeaders().add("Content-Length", String.valueOf(3 * 1024 * 1024));
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        // 不支持 HEAD 的站点
        server.createContext("/no-head.png", exchange -> {
            if ("HEAD".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            respond(exchange, 200, "image/png", TINY_PNG);
        });
        // 不存在的图片
        server.createContext("/missing.png", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** 统一的响应输出 */
    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status,
                                String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.getResponseHeaders().add("Content-Length", String.valueOf(body.length));
        if ("HEAD".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    /* ------------------------------ 入参与协议校验 ------------------------------ */

    @Test
    @DisplayName("空地址应被拒绝")
    void blankUrlShouldFail() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> urlPictureUpload.validPicture("   "));
        assertEquals("文件地址不能为空", e.getMessage());
    }

    @Test
    @DisplayName("格式非法的地址应被拒绝")
    void malformedUrlShouldFail() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> urlPictureUpload.validPicture("not-a-url"));
        assertEquals("文件地址格式不正确", e.getMessage());
    }

    @Test
    @DisplayName("非 http/https 协议应被拒绝")
    void nonHttpSchemeShouldFail() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> urlPictureUpload.validPicture("ftp://example.com/a.png"));
        assertTrue(e.getMessage().contains("HTTP"), e.getMessage());
    }

    /* ------------------------------ 类型与体积校验 ------------------------------ */

    @Test
    @DisplayName("URL 后缀不是图片时应被拒绝")
    void wrongSuffixShouldFail() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> urlPictureUpload.validPicture(baseUrl + "/text.txt"));
        assertTrue(e.getMessage().contains("文件类型错误"), e.getMessage());
    }

    @Test
    @DisplayName("Content-Type 不是图片时应被拒绝")
    void wrongContentTypeShouldFail() {
        // 后缀伪装成 png，但服务端返回 text/plain
        BusinessException e = assertThrows(BusinessException.class,
                () -> urlPictureUpload.validPicture(baseUrl + "/fake.png"));
        assertTrue(e.getMessage().contains("Content-Type") || e.getMessage().contains("图片"),
                e.getMessage());
    }

    @Test
    @DisplayName("Content-Length 超过 2MB 时应被拒绝")
    void tooLargeShouldFail() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> urlPictureUpload.validPicture(baseUrl + "/big.png"));
        assertEquals("文件大小不能超过 2M", e.getMessage());
    }

    /* ------------------------------ 正常与容错 ------------------------------ */

    @Test
    @DisplayName("合法图片地址应通过校验")
    void goodUrlShouldPass() {
        assertDoesNotThrow(() -> urlPictureUpload.validPicture(baseUrl + "/good.png"));
    }

    @Test
    @DisplayName("图片不存在时不应在 HEAD 阶段直接失败，交给下载阶段报错")
    void missingUrlShouldNotFailAtValidation() {
        // HEAD 返回 404 时选择放行，由下载阶段给出更准确的错误
        assertDoesNotThrow(() -> urlPictureUpload.validPicture(baseUrl + "/missing.png"));
    }

    @Test
    @DisplayName("站点不支持 HEAD 时不应阻塞上传")
    void headNotAllowedShouldPass() {
        assertDoesNotThrow(() -> urlPictureUpload.validPicture(baseUrl + "/no-head.png"));
    }

    /* ------------------------------ 文件名解析 ------------------------------ */

    @Test
    @DisplayName("应从 URL 中解析出带后缀的文件名")
    void shouldExtractFilename() {
        assertEquals("photo.jpg", urlPictureUpload.getOriginFilename("https://example.com/a/b/photo.jpg"));
    }

    @Test
    @DisplayName("解析文件名时应忽略 query 参数")
    void shouldIgnoreQueryString() {
        assertEquals("photo.png",
                urlPictureUpload.getOriginFilename("https://example.com/a/photo.png?x=1&y=2"));
    }

    @Test
    @DisplayName("解析文件名时应处理百分号编码")
    void shouldDecodeEncodedFilename() {
        assertEquals("图片.png",
                urlPictureUpload.getOriginFilename("https://example.com/a/%E5%9B%BE%E7%89%87.png"));
    }

    @Test
    @DisplayName("URL 无文件名时应返回兜底名，保证临时文件前缀合法")
    void shouldFallbackWhenNoFilename() {
        assertEquals("picture.jpg", urlPictureUpload.getOriginFilename("https://example.com/"));
    }

    @Test
    @DisplayName("文件名过短时应返回兜底名")
    void shouldFallbackWhenFilenameTooShort() {
        assertEquals("picture.jpg", urlPictureUpload.getOriginFilename("https://example.com/a"));
    }
}
