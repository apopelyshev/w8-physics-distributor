package pl.edu.pwr.w8;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import javax.servlet.http.HttpServletRequest;

import net.rubyeye.xmemcached.MemcachedClient;
import net.rubyeye.xmemcached.MemcachedClientBuilder;
import net.rubyeye.xmemcached.XMemcachedClientBuilder;
import net.rubyeye.xmemcached.auth.AuthInfo;
import net.rubyeye.xmemcached.command.BinaryCommandFactory;
import net.rubyeye.xmemcached.exception.MemcachedException;
import net.rubyeye.xmemcached.utils.AddrUtil;

public class CacheService {
  private MemcachedClient memcachedInstance;
  
  public CacheService() {
    try { memcachedInstance = buildCacheClient(); }
    catch (IOException ioe) { ioe.printStackTrace(); }
  }
  
  protected static MemcachedClient buildCacheClient() throws IOException {
    String valueFromEnv = System.getenv("MEMCACHIER_SERVERS").replace(",", " ");
    List<InetSocketAddress> servers = AddrUtil.getAddresses(valueFromEnv);
    AuthInfo authInfo = AuthInfo.plain(
        System.getenv("MEMCACHIER_USERNAME"),
        System.getenv("MEMCACHIER_PASSWORD")
    );
    
    MemcachedClientBuilder builder = new XMemcachedClientBuilder(servers);
    for(InetSocketAddress server : servers) {
      builder.addAuthInfo(server, authInfo);
    }
    builder.setCommandFactory(new BinaryCommandFactory());
    builder.setConnectTimeout(1000);
    builder.setEnableHealSession(true);
    builder.setHealSessionInterval(2000);
    return builder.build();
  }
  
  protected boolean setOrDelCached(String... args) {
    try {
      if (args.length>1) memcachedInstance.set(args[0], 0, args[1]);
      else memcachedInstance.delete(args[0]);
      
      return true;
    } catch (TimeoutException | InterruptedException | MemcachedException e) {
      e.printStackTrace();
    }
    return false;
  }
  
  protected String getCached(String key) {
    String value = "";
    try {
      value = memcachedInstance.get(key);
    } catch (TimeoutException | InterruptedException | MemcachedException e) {
      e.printStackTrace();
    }
    return value;
  }
  
  protected String generateTrackKey(Person p) {
    return p.getName().toUpperCase()+"_TRACK";
  }
  
  //Get client by the known hex code
  protected Person getMemberByCode(HttpServletRequest req, String codeFromServlet, PersonArr arrOfAllMembers) {
    for (Person smb : arrOfAllMembers.getArr()) {
      String generatedKey = generateTrackKey(smb);
      Optional<String> valFromCache = Optional.ofNullable(getCached(generatedKey));
      boolean codeMatchesToEnvCode = System.getenv(generatedKey).equals(codeFromServlet);
      
      if (codeMatchesToEnvCode && !valFromCache.isPresent()) {
        setOrDelCached(generatedKey, Util.getMAC(req));
        return smb;
      } else if (codeMatchesToEnvCode)
        return smb;
    }
    return null;
  }
  
  // Get client if his/her MAC exists in cache
  protected Person getMemberByMAC(HttpServletRequest req, PersonArr arrOfAllMembers) {
    for (Person smb : arrOfAllMembers.getArr()) {
      String generatedKey = generateTrackKey(smb);
      Optional<String> valFromCache = Optional.ofNullable(getCached(generatedKey));
      
      if (valFromCache.isPresent() && valFromCache.orElse("").indexOf(Util.getMAC(req)) >= 0)
        return smb;
    }
    return null;
  }
  
  // Erase all tracked MACs from cache (needed for debug)
  protected void handleAllMemberTrackings(PersonArr arrOfAllMembers, String action) {
    StringBuilder sb = new StringBuilder();
    boolean printNeeded = action.equals("print");
    for (Person smb : arrOfAllMembers.getArr()) {
      String generatedKey = generateTrackKey(smb);
      Optional<String> valFromCache = Optional.ofNullable(getCached(generatedKey));
      
      if (valFromCache.isPresent()) {
        if (printNeeded)
          sb.append(valFromCache+"\n");
        else
          setOrDelCached(generatedKey);
      }
    }
    if (printNeeded) action = sb.toString();
  }
  
  protected MemcachedClient getInstance() { return memcachedInstance; }
}
