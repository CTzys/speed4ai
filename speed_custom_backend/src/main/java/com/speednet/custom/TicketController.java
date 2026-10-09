package com.speednet.custom;

import com.speednet.custom.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import java.util.Map;

/** Only fixed support endpoints are forwarded. Tenant is always supplied by server configuration. */
@RestController
@RequestMapping("/custom-api/tickets")
public class TicketController {
    private final RestClient backend;
    private final String tenant;
    private final String backendUrl;
    public TicketController(@Value("${custom.backend-url:http://127.0.0.1:48080}") String url, @Value("${custom.tenant-id}") String tenant) { var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build()); factory.setReadTimeout(java.time.Duration.ofSeconds(15)); backend = RestClient.builder().baseUrl(url).requestFactory(factory).build(); this.tenant = tenant; this.backendUrl = url.replaceAll("/+$", ""); }
    @ModelAttribute public void noCache(jakarta.servlet.http.HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }
    private RestClient.RequestBodySpec request(HttpServletRequest r, HttpMethod method, String path) {
        return backend.method(method).uri(java.net.URI.create(backendUrl + path)).header("Authorization", r.getHeader("Authorization")).header("tenant-id", tenant);
    }
    private Object forward(HttpServletRequest r, HttpMethod method, String path, Map<String, Object> body) {
        try {
            var req = request(r, method, path);
            if (body != null) req.contentType(MediaType.APPLICATION_JSON).body(body);
            return req.retrieve().body(Map.class);
        } catch (RestClientException ex) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "工单服务暂时不可用，请稍后重试"); }
    }
    @GetMapping public Object page(HttpServletRequest r, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size, @RequestParam(defaultValue="") String status, @RequestParam(defaultValue="") String category, @RequestParam(defaultValue="") String keyword) {
        String path = org.springframework.web.util.UriComponentsBuilder.fromPath("/app-api/support/tickets").queryParam("page", page).queryParam("size", size).queryParam("status", status).queryParam("category", category).queryParam("keyword", keyword).build().encode().toUriString();
        return forward(r, HttpMethod.GET, path, null);
    }
    @GetMapping("/unread") public Object unread(HttpServletRequest r) { return forward(r, HttpMethod.GET, "/app-api/support/tickets/unread", null); }
    @PostMapping public Object create(HttpServletRequest r, @RequestBody Map<String, Object> body) { return forward(r, HttpMethod.POST, "/app-api/support/tickets", body); }
    @GetMapping("/{id}") public Object detail(HttpServletRequest r, @PathVariable long id, @RequestParam(defaultValue="0") long beforeSeq) { return forward(r, HttpMethod.GET, "/app-api/support/tickets/" + id + "?beforeSeq=" + beforeSeq, null); }
    @PostMapping("/{id}/{action:reply|read|action}") public Object action(HttpServletRequest r, @PathVariable long id, @PathVariable String action, @RequestBody Map<String, Object> body) { return forward(r, HttpMethod.POST, "/app-api/support/tickets/" + id + "/" + action, body); }
    @PostMapping("/attachments") public Object upload(HttpServletRequest r, @RequestParam MultipartFile file) throws java.io.IOException {
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) throw new ApiException(HttpStatus.BAD_REQUEST, "截图不能超过 5 MB");
        try {
            var form = new LinkedMultiValueMap<String, Object>(); form.add("file", file.getResource());
            return request(r, HttpMethod.POST, "/app-api/support/tickets/attachments").contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().body(Map.class);
        } catch (RestClientException ex) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "截图上传失败，请稍后重试"); }
    }
    @DeleteMapping("/attachments/{id}") public Object removeAttachment(HttpServletRequest r, @PathVariable long id) { return forward(r, HttpMethod.DELETE, "/app-api/support/tickets/attachments/" + id, null); }
    @GetMapping("/attachments/{id}") public ResponseEntity<byte[]> attachment(HttpServletRequest r, @PathVariable long id) {
        try {
            var response = request(r, HttpMethod.GET, "/app-api/support/tickets/attachments/" + id).retrieve().toEntity(byte[].class);
            var type = response.getHeaders().getContentType();
            if (type == null || !java.util.Set.of("image/png", "image/jpeg", "image/webp").contains(type.toString())) throw new ApiException(HttpStatus.NOT_FOUND, "截图不存在或无权访问");
            return ResponseEntity.ok().contentType(type).header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff").body(response.getBody());
        } catch (RestClientException ex) { throw new ApiException(HttpStatus.NOT_FOUND, "截图不存在或无权访问"); }
    }
}
