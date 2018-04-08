package cn.leancloud.cache;

import android.content.Context;
import android.content.SharedPreferences;

import java.lang.reflect.TypeVariable;

import cn.leancloud.AVLogger;
import cn.leancloud.utils.LogUtil;

/**
 * Created by fengjunwen on 2018/4/8.
 */

public class AndroidSystemSetting implements SystemSetting {
  private static AVLogger LOGGER = LogUtil.getLogger(AndroidSystemSetting.class);

  private Context context;

  public AndroidSystemSetting(Context context) {
    this.context = context;
  }

  public boolean getBoolean(String keyZone, String key, boolean defaultValue) {
    if (null == this.context) {
      LOGGER.w("application context is null");
      return defaultValue;
    }
    SharedPreferences setting = this.context.getSharedPreferences(keyZone, Context.MODE_PRIVATE);
    return setting.getBoolean(key, defaultValue);
  }

  public int getInteger(String keyZone, String key, int defaultValue) {
    if (null == this.context) {
      LOGGER.w("application context is null");
      return defaultValue;
    }
    SharedPreferences setting = this.context.getSharedPreferences(keyZone, Context.MODE_PRIVATE);
    return setting.getInt(key, defaultValue);
  }

  public float getFloat(String keyZone, String key, float defaultValue) {
    if (null == this.context) {
      LOGGER.w("application context is null");
      return defaultValue;
    }
    SharedPreferences setting = this.context.getSharedPreferences(keyZone, Context.MODE_PRIVATE);
    return setting.getFloat(key, defaultValue);
  }

  public long getLong(String keyZone, String key, long defaultValue) {
    if (null == this.context) {
      LOGGER.w("application context is null");
      return defaultValue;
    }
    SharedPreferences setting = this.context.getSharedPreferences(keyZone, Context.MODE_PRIVATE);
    return setting.getLong(key, defaultValue);
  }

  public String getString(String keyZone, String key, String defaultValue) {
    if (null == this.context) {
      LOGGER.w("application context is null");
      return defaultValue;
    }
    SharedPreferences setting = this.context.getSharedPreferences(keyZone, Context.MODE_PRIVATE);
    return setting.getString(key, defaultValue);
  }

  public void saveBoolean(String keyZone, String key, boolean value) {
    if (null == this.context) {
      LOGGER.w("application context is null");
      return;
    }
    SharedPreferences setting = this.context.getSharedPreferences(keyZone, Context.MODE_PRIVATE);
    SharedPreferences.Editor editor = setting.edit();
    editor.putBoolean(key, value);
    editor.commit();
  }
  public void saveInteger(String keyZone, String key, int value) {
    if (null == this.context) {
      LOGGER.w("application context is null");
      return;
    }
    SharedPreferences setting = this.context.getSharedPreferences(keyZone, Context.MODE_PRIVATE);
    SharedPreferences.Editor editor = setting.edit();
    editor.putInt(key, value);
    editor.commit();
  }
  public void saveFloat(String keyZone, String key, float value) {
    if (null == this.context) {
      LOGGER.w("application context is null");
      return;
    }
    SharedPreferences setting = this.context.getSharedPreferences(keyZone, Context.MODE_PRIVATE);
    SharedPreferences.Editor editor = setting.edit();
    editor.putFloat(key, value);
    editor.commit();
  }
  public void saveLong(String keyZone, String key, long value) {
    if (null == this.context) {
      LOGGER.w("application context is null");
      return;
    }
    SharedPreferences setting = this.context.getSharedPreferences(keyZone, Context.MODE_PRIVATE);
    SharedPreferences.Editor editor = setting.edit();
    editor.putLong(key, value);
    editor.commit();
  }
  public void saveString(String keyZone, String key, String value) {
    if (null == this.context) {
      LOGGER.w("application context is null");
      return;
    }
    SharedPreferences setting = this.context.getSharedPreferences(keyZone, Context.MODE_PRIVATE);
    SharedPreferences.Editor editor = setting.edit();
    editor.putString(key, value);
    editor.commit();
  }

  public void removeKey(String keyZone, String key) {
    if (null == this.context) {
      LOGGER.w("application context is null");
    }
    SharedPreferences setting = this.context.getSharedPreferences(keyZone, Context.MODE_PRIVATE);
    SharedPreferences.Editor editor = setting.edit();
    editor.remove(key);
    editor.commit();
  }

  public void removeKeyZone(String keyZone) {
    if (null == this.context) {
      LOGGER.w("application context is null");
    }
    SharedPreferences setting = this.context.getSharedPreferences(keyZone, Context.MODE_PRIVATE);
    SharedPreferences.Editor editor = setting.edit();
    editor.clear();
    editor.commit();
  }

}