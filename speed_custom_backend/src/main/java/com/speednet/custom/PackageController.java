package com.speednet.custom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.speednet.custom.common.ApiException;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
/** Reuses the authenticated customer session; never accepts a client-supplied member or tenant ID. */
@RestController @RequestMapping("/custom-api/packages")
public class PackageController {
 @ModelAttribute public void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
 private final RestClient backend;private final String tenant;
 public PackageController(@Value("${custom.backend-url:http://127.0.0.1:48080}") String url,@Value("${custom.tenant-id}") String tenant){backend=RestClient.create(url);this.tenant=tenant;}
 Object forward(String action,HttpServletRequest request,Map<String,Object> body){try{
  var base=backend.method(body==null?org.springframework.http.HttpMethod.GET:org.springframework.http.HttpMethod.POST).uri("/app-api/subscription-products/"+action).header("Authorization",request.getHeader("Authorization")).header("tenant-id",tenant).header("X-Forwarded-For",request.getRemoteAddr());
  if(body!=null)base.contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(body);
  return base.retrieve().body(Map.class);
 }catch(RestClientException e){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"套餐服务暂时不可用，请稍后重试");}}
 @GetMapping({"/plans","/current","/orders","/channels","/link","/regions"}) public Object get(HttpServletRequest request){return forward(request.getRequestURI().substring(request.getRequestURI().lastIndexOf('/')+1),request,null);}
 @GetMapping("/info") public Object info(HttpServletRequest request){return forward("info",request,null);}
 @PostMapping({"/checkout","/pay","/refresh","/cancel"}) public Object post(@RequestBody Map<String,Object> body,HttpServletRequest request){return forward(request.getRequestURI().substring(request.getRequestURI().lastIndexOf('/')+1),request,body);}
 org.springframework.http.ResponseEntity<String> clash(HttpServletRequest request){try{
  var upstream=backend.get().uri("/app-api/subscription-products/clash").header("Authorization",request.getHeader("Authorization"))
   .header("tenant-id",tenant).retrieve().toEntity(String.class);
  var headers=new org.springframework.http.HttpHeaders();
  for(String name:java.util.List.of("Content-Type","Content-Disposition","subscription-userinfo","profile-update-interval")){
   String value=upstream.getHeaders().getFirst(name);if(value!=null)headers.set(name,value);
  }
  headers.set("Cache-Control","no-store");return new org.springframework.http.ResponseEntity<>(upstream.getBody(),headers,upstream.getStatusCode());
 }catch(org.springframework.web.client.RestClientResponseException e){return org.springframework.http.ResponseEntity.status(e.getStatusCode())
  .contentType(org.springframework.http.MediaType.APPLICATION_JSON).header("Cache-Control","no-store").body(e.getResponseBodyAsString());
 }catch(RestClientException e){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"订阅服务暂时不可用，请稍后重试");}}

}
