package com.speednet.module.xray.service.node;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Socks5NodeParserTest {
    @Test void encodedCredentialsAndNames(){
        var n=Socks5NodeParser.parse("socks5://a%40b:p%3A%2F%23+q@Example.COM:1080#%E9%A6%99%E6%B8%AF");
        assertEquals("example.com",n.getHost());assertEquals("a@b",n.getUsername());assertEquals("p:/#+q",n.getPassword());assertEquals("香港",n.getName());
    }
    @Test void ipv6AndNoAuthentication(){
        var n=Socks5NodeParser.parse("socks5://[2001:db8::1]:65535#ipv6");
        assertEquals("2001:db8::1",n.getHost());assertEquals(0,n.getAuthType());assertEquals("",n.getUsername());
    }
    @Test void rejectMalformedLinksWithoutLeakingSecrets(){
        for(String uri:new String[]{"http://user:TOPSECRET@host:80","socks5://host","socks5://user:TOPSECRET@host:65536","socks5://user@host:1080","socks5://host:1080/path","socks5://host:1080?secret=TOPSECRET","socks5://:pass@host:1080"}){
            var e=assertThrows(IllegalArgumentException.class,()->Socks5NodeParser.parse(uri));assertFalse(e.getMessage().contains("TOPSECRET"));
        }
    }
    @Test void identityNormalizesAddressAndIgnoresPasswordButKeepsAccount(){
        var a=Socks5NodeParser.parse("socks5://u:one@Example.com.:1080");var b=Socks5NodeParser.parse("socks://u:two@example.com:1080");
        assertEquals(Socks5NodeParser.identity(a),Socks5NodeParser.identity(b));
        assertNotEquals(Socks5NodeParser.identity(a),Socks5NodeParser.identity(Socks5NodeParser.parse("socks5://U:one@example.com:1080")));
    }
    @Test void rfc1929LimitIsBytes(){assertThrows(IllegalArgumentException.class,()->Socks5NodeParser.parse("socks5://u:"+"密".repeat(100)+"@host:1080"));}
    @Test void compactFormatPreservesLiteralCredentialsAndDeduplicatesAgainstUri(){
        var compact=Socks5NodeParser.parse("  1.2.3.4:25906:user:p%40+word:#end  ");
        assertEquals("1.2.3.4",compact.getHost());assertEquals(25906,compact.getPort());assertEquals("user",compact.getUsername());assertEquals("p%40+word:#end",compact.getPassword());assertEquals(1,compact.getAuthType());
        var uri=Socks5NodeParser.parse("socks5://user:other@1.2.3.4:25906");
        assertEquals(Socks5NodeParser.identity(compact),Socks5NodeParser.identity(uri));
    }
    @Test void compactFormatSupportsBracketedIpv6AndDomain(){
        assertEquals("2001:db8::1",Socks5NodeParser.parse("[2001:db8::1]:1080:u:p").getHost());
        assertEquals("example.com",Socks5NodeParser.parse("Example.COM:1080:u:p").getHost());
    }
    @Test void invalidCompactFormatDoesNotLeakCredentials(){
        for(String text:new String[]{"1.2.3.4:0:u:TOPSECRET","1.2.3.4:65536:u:TOPSECRET","1.2.3.4:1080::TOPSECRET","1.2.3.4:1080:u:","1.2.3.4:999999999999:u:TOPSECRET"}){
            var error=assertThrows(IllegalArgumentException.class,()->Socks5NodeParser.parse(text));assertFalse(error.getMessage().contains("TOPSECRET"));
        }
    }
}
