package tr.lidertv.app;
import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.regex.*;
/** Streaming parser: never loads a complete playlist into RAM. */
public final class M3uParser {
    public interface Sink { void accept(Entry e) throws Exception; }
    public static final class Entry {
        public String title="", url="", group="Di\u011fer", kind="live", poster="", cast="", plot="", userAgent="", referer="";
        public long added=0; public boolean inferred=false;
    }
    public static final class Stats { public long records, skipped, inferred; }
    private static final Pattern ATTR=Pattern.compile("([\\w-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s,]+))");
    public static Map<String,String> attributes(String line) {
        Map<String,String> a=new HashMap<>(); Matcher m=ATTR.matcher(line);
        while(m.find()) a.put(m.group(1).toLowerCase(Locale.ROOT),m.group(2)!=null?m.group(2):m.group(3)!=null?m.group(3):m.group(4));
        return a;
    }
    static int lastHeaderComma(String s) {
        char quote=0; for(int i=0;i<s.length();i++) {
            char c=s.charAt(i); if((c=='\"'||c=='\'') && (quote==0||quote==c)) quote=quote==0?c:0;
            if(c==','&&quote==0) return i;
        } return -1;
    }
    public static boolean validUrl(String url) {
        try { URI u=URI.create(url);return Arrays.asList("http","https","rtsp","rtmp","rtsps").contains(u.getScheme()==null?"":u.getScheme().toLowerCase(Locale.ROOT)) && u.getHost()!=null; }
        catch(Exception e) {return false;}
    }
    public static String canonical(String url) {
        try { URI u=URI.create(url);String scheme=u.getScheme().toLowerCase(Locale.ROOT);String host=u.getHost().toLowerCase(Locale.ROOT);
            if(host.indexOf(':')>=0&&!host.startsWith("["))host="["+host+"]";
            String auth=(u.getRawUserInfo()==null?"":u.getRawUserInfo()+"@")+host+(u.getPort()==-1?"":":"+u.getPort());
            return scheme+"://"+auth+(u.getRawPath()==null?"":u.getRawPath())+(u.getRawQuery()==null?"":"?"+u.getRawQuery());
        }catch(Exception e){return url;}
    }
    public static Stats parse(Reader source, Sink sink) throws Exception {
        BufferedReader r=source instanceof BufferedReader?(BufferedReader)source:new BufferedReader(source,65536);
        Stats st=new Stats(); String line; Entry entry=null; boolean header=false;
        while((line=r.readLine())!=null) {
            line=line.replace("\ufeff","").trim(); if(line.isEmpty())continue;
            if(line.startsWith("#EXT-X-TARGETDURATION")||line.startsWith("#EXT-X-STREAM-INF"))throw new Text.UserError("Bu adres bir HLS ak\u0131\u015f\u0131d\u0131r; kanal katalog listesi de\u011fildir.");
            if(line.startsWith("#EXTM3U")){header=true;continue;}
            if(line.startsWith("#EXTINF:")) {
                if(entry!=null)st.skipped++;
                header=true; entry=new Entry();int c=lastHeaderComma(line); Map<String,String>a=attributes(c>=0?line.substring(0,c):line);
                entry.title=c>=0?line.substring(c+1).trim():a.getOrDefault("tvg-name","");
                if(entry.title.isEmpty())entry.title=a.getOrDefault("tvg-name","Ads\u0131z i\u00e7erik");
                entry.group=a.getOrDefault("group-title","Di\u011fer"); if(entry.group.trim().isEmpty())entry.group="Di\u011fer";
                entry.poster=a.getOrDefault("tvg-logo","");entry.cast=a.getOrDefault("cast",a.getOrDefault("actors",""));
                entry.plot=a.getOrDefault("plot",a.getOrDefault("description",""));
                entry.kind=a.getOrDefault("type",a.getOrDefault("stream-type",""));
                try{entry.added=Long.parseLong(a.getOrDefault("added","0")); if(entry.added<100000000000L)entry.added*=1000;}catch(Exception ignored){}
                continue;
            }
            if(entry!=null && line.startsWith("#EXTGRP:")){entry.group=line.substring(8).trim();continue;}
            if(entry!=null && line.startsWith("#EXTVLCOPT:")) {
                String opt=line.substring(11);int eq=opt.indexOf('='); if(eq>0) {
                    String k=opt.substring(0,eq),v=opt.substring(eq+1);
                    if(k.equals("http-user-agent"))entry.userAgent=v;
                    if(k.equals("http-referrer")||k.equals("http-referer"))entry.referer=v;
                } continue;
            }
            if(line.startsWith("#")) continue;
            if(entry==null){st.skipped++;continue;}
            entry.url=line;
            if(!validUrl(line)){st.skipped++;entry=null;continue;}
            String k=Text.fold(entry.kind);
            if(k.equals("movie")||k.equals("movies")||k.equals("vod")||k.equals("film"))entry.kind="movies";
            else if(k.equals("series")||k.equals("dizi"))entry.kind="series";
            else if(k.equals("live")||k.equals("tv"))entry.kind="live";
            else {
                entry.inferred=true;st.inferred++;String path=URI.create(line).getPath().toLowerCase(Locale.ROOT);String g=Text.fold(entry.group);
                if(path.contains("/series/")||g.matches(".*\\b(dizi|series)\\b.*"))entry.kind="series";
                else if(path.contains("/movie/")||g.matches(".*\\b(film|filmler|vod|movie|movies)\\b.*")||path.matches(".*\\.(mp4|mkv|avi|mov|webm)$"))entry.kind="movies";
                else entry.kind="live";
            }
            sink.accept(entry);st.records++;entry=null;
        }
        if(entry!=null)st.skipped++;
        if(!header || st.records==0)throw new Text.UserError("Ge\u00e7erli bir M3U kanal listesi bulunamad\u0131. HLS medya par\u00e7a listesi, kaynak listesi de\u011fildir.");
        return st;
    }
}
