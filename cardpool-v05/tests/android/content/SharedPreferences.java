package android.content;
import java.util.Map;
public interface SharedPreferences {
 String getString(String key,String fallback);long getLong(String key,long fallback);
 default Map<String,?> getAll(){return java.util.Collections.emptyMap();}
 Editor edit();interface Editor {Editor putString(String key,String value);Editor putLong(String key,long value);void apply();default boolean commit(){apply();return true;}}
}
