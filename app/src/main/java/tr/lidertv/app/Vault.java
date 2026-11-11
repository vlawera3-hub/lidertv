package tr.lidertv.app;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
/** Device-bound encryption; backups are disabled. PIN is a local parental control, not DRM. */
public final class Vault {
    private final SharedPreferences prefs; private volatile SecretKey cached; private static final String ALIAS="LiderTv.SourceKey.v1";
    public static volatile boolean unlocked=false;
    public Vault(Context c){prefs=c.getSharedPreferences("security",Context.MODE_PRIVATE);}
    private synchronized SecretKey key() throws Exception {
        if(cached!=null)return cached; KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)){
            KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();
        }
        cached=((KeyStore.SecretKeyEntry)store.getEntry(ALIAS,null)).getSecretKey();return cached;
    }
    public String encrypt(String text)throws Exception{
        if(text==null||text.isEmpty())return "";Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
        return Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)),Base64.NO_WRAP);
    }
    public String decrypt(String blob)throws Exception{
        if(blob==null||blob.isEmpty())return "";String[] a=blob.split(":",2);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(a[0],Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(a[1],Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
    }
    public boolean hasPin(){return prefs.contains("pin");}
    private byte[] hash(String pin,byte[] salt)throws Exception{
        PBEKeySpec s=new PBEKeySpec(pin.toCharArray(),salt,120000,256);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(s).getEncoded();}finally{s.clearPassword();}
    }
    public synchronized void setPin(String pin)throws Exception{
        requireAdmin();if(!pin.matches("[0-9]{4,8}"))throw new Text.UserError("PIN, 4-8 rakamdan olu\u015fmal\u0131.");
        byte[]salt=new byte[24];new SecureRandom().nextBytes(salt);
        prefs.edit().putString("salt",Base64.encodeToString(salt,Base64.NO_WRAP)).putString("pin",Base64.encodeToString(hash(pin,salt),Base64.NO_WRAP)).putInt("failures",0).remove("until").commit();
        unlocked=true;
    }
    public synchronized void unlock(String pin)throws Exception{
        if(!hasPin())throw new Text.UserError("\u00d6nce ebeveyn PIN'i olu\u015fturun.");
        long until=prefs.getLong("until",0);if(System.currentTimeMillis()<until)throw new Text.UserError("\u00c7ok say\u0131da yanl\u0131\u015f deneme. 30 saniye sonra yeniden deneyin.");
        boolean ok=MessageDigest.isEqual(hash(pin,Base64.decode(prefs.getString("salt",""),Base64.NO_WRAP)),Base64.decode(prefs.getString("pin",""),Base64.NO_WRAP));
        if(!ok){int f=prefs.getInt("failures",0)+1;prefs.edit().putInt("failures",f).putLong("until",f%5==0?System.currentTimeMillis()+30000:0).commit();throw new Text.UserError("PIN yanl\u0131\u015f.");}
        prefs.edit().putInt("failures",0).remove("until").commit();unlocked=true;
    }
    public void requireAdmin()throws Text.UserError{if(hasPin()&&!unlocked)throw new Text.UserError("Bu i\u015flem i\u00e7in Ayarlar > Ebeveyn denetimi b\u00f6l\u00fcm\u00fcnden PIN ile oturumu a\u00e7\u0131n.");}
}
